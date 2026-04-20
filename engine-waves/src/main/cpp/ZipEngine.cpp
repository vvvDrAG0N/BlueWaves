#include <jni.h>
#include <string>
#include <vector>
#include <zlib.h>
#include <android/log.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <utime.h>
#include <sstream>
#include "IEngineComponent.h"
#include "TitanLog.h"

class NativeZipSource : public IDocumentSource {
public:
    bool open(const std::string& path) override {
        // Implementation logic
        return true;
    }
    
    std::string getRawContent(const std::string& entryId) override {
        return ""; // To be implemented in Phase 4
    }
};

#define TAG "ZipEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

/**
 * Titan Native Unzipper
 * 
 * Optimized for Android IO with large buffer sizes and minimal memory copies.
 */

// Helper to create directories recursively
void make_dir(const std::string& path) {
    std::string current_level = "";
    std::string level;
    std::stringstream ss(path);

    while (std::getline(ss, level, '/')) {
        current_level += level + "/";
        mkdir(current_level.c_str(), 0777);
    }
}

/**
 * Core Decompression Logic (zlib)
 */
bool decompress_file(const std::string& zip_path, long offset, long compressed_size, const std::string& out_path) {
    FILE* src = fopen(zip_path.c_str(), "rb");
    if (!src) return false;
    
    fseek(src, offset, SEEK_SET);
    
    FILE* dst = fopen(out_path.c_str(), "wb");
    if (!dst) {
        fclose(src);
        return false;
    }

    z_stream strm;
    strm.zalloc = Z_NULL;
    strm.zfree = Z_NULL;
    strm.opaque = Z_NULL;
    strm.avail_in = 0;
    strm.next_in = Z_NULL;

    // Use -15 for raw deflate (standard ZIP)
    if (inflateInit2(&strm, -15) != Z_OK) {
        fclose(src);
        fclose(dst);
        return false;
    }

    const int CHUNK = 128 * 1024; // 128KB buffer
    unsigned char in[CHUNK];
    unsigned char out[CHUNK];

    long remaining = compressed_size;
    int ret;

    do {
        strm.avail_in = fread(in, 1, (remaining < CHUNK) ? remaining : CHUNK, src);
        if (strm.avail_in == 0) break;
        remaining -= strm.avail_in;
        strm.next_in = in;

        do {
            strm.avail_out = CHUNK;
            strm.next_out = out;
            ret = inflate(&strm, Z_NO_FLUSH);
            
            if (ret == Z_NEED_DICT || ret == Z_DATA_ERROR || ret == Z_MEM_ERROR) {
                inflateEnd(&strm);
                fclose(src);
                fclose(dst);
                return false;
            }

            int have = CHUNK - strm.avail_out;
            if (fwrite(out, 1, have, dst) != have) {
                inflateEnd(&strm);
                fclose(src);
                fclose(dst);
                return false;
            }
        } while (strm.avail_out == 0);
    } while (ret != Z_STREAM_END && remaining > 0);

    inflateEnd(&strm);
    fclose(src);
    fclose(dst);
    return true;
}

#pragma pack(push, 1)
struct CentralDirectoryHeader {
    uint32_t signature;
    uint16_t versionMadeBy;
    uint16_t versionNeeded;
    uint16_t flags;
    uint16_t compressionMethod;
    uint16_t lastModTime;
    uint16_t lastModDate;
    uint32_t crc32;
    uint32_t compressedSize;
    uint32_t uncompressedSize;
    uint16_t fileNameLength;
    uint16_t extraFieldLength;
    uint16_t fileCommentLength;
    uint16_t diskNumberStart;
    uint16_t internalFileAttributes;
    uint32_t externalFileAttributes;
    uint32_t localHeaderOffset;
};

struct EndOfCentralDirectoryRecord {
    uint32_t signature;
    uint16_t diskNumber;
    uint16_t startDiskNumber;
    uint16_t numberCentralDirectoryRecordsOnThisDisk;
    uint16_t totalNumberCentralDirectoryRecords;
    uint32_t centralDirectorySize;
    uint32_t centralDirectoryOffset;
    uint16_t commentLength;
};
#pragma pack(pop)

extern "C" JNIEXPORT jboolean JNICALL
Java_com_epubreader_engine_WavesEngine_extractEpub(
        JNIEnv* env,
        jobject /* this */,
        jstring zipPath,
        jstring destPath) {

    TitanTimer timer("NativeExtractEpub");
    
    const char* cZipPath = env->GetStringUTFChars(zipPath, nullptr);
    const char* cDestPath = env->GetStringUTFChars(destPath, nullptr);

    std::string zipPathStr(cZipPath);
    std::string destPathStr(cDestPath);

    FILE* file = fopen(cZipPath, "rb");
    if (!file) {
        env->ReleaseStringUTFChars(zipPath, cZipPath);
        env->ReleaseStringUTFChars(destPath, cDestPath);
        return JNI_FALSE;
    }

    // Find EOCD (simplified)
    fseek(file, -22, SEEK_END);
    EndOfCentralDirectoryRecord eocd;
    fread(&eocd, sizeof(eocd), 1, file);

    if (eocd.signature != 0x06054b50) {
        LOGE("Invalid ZIP Signature");
        env->ReleaseStringUTFChars(destPath, cDestPath);
        return JNI_FALSE;
    }

    fseek(file, eocd.centralDirectoryOffset, SEEK_SET);

    for (int i = 0; i < eocd.totalNumberCentralDirectoryRecords; ++i) {
        CentralDirectoryHeader header;
        fread(&header, sizeof(header), 1, file);
        
        std::vector<char> fileName(header.fileNameLength + 1);
        fread(fileName.data(), 1, header.fileNameLength, file);
        fileName[header.fileNameLength] = '\0';
        
        // Skip extra field and comment
        fseek(file, header.extraFieldLength + header.fileCommentLength, SEEK_CUR);

        std::string entryName(fileName.data());
        std::string fullOutPath = destPathStr + "/" + entryName;

        TLOGI("[ZIP] Extracting: %s", entryName.c_str());

        if (entryName.back() == '/') {
            make_dir(fullOutPath);
        } else {
            // Ensure directory exists
            size_t lastSlash = fullOutPath.find_last_of('/');
            if (lastSlash != std::string::npos) {
                make_dir(fullOutPath.substr(0, lastSlash));
            }

            // Get local header to find exact data offset
            long currentPos = ftell(file);
            fseek(file, header.localHeaderOffset, SEEK_SET);
            uint32_t localSig;
            fread(&localSig, 4, 1, file);
            fseek(file, 22, SEEK_CUR); // Skip to lengths
            uint16_t nLen, eLen;
            fread(&nLen, 2, 1, file);
            fread(&eLen, 2, 1, file);
            long dataOffset = header.localHeaderOffset + 30 + nLen + eLen;
            
            decompress_file(zipPathStr, dataOffset, header.compressedSize, fullOutPath);
            fseek(file, currentPos, SEEK_SET);
        }
    }

    fclose(file);
    env->ReleaseStringUTFChars(zipPath, cZipPath);
    env->ReleaseStringUTFChars(destPath, cDestPath);

    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_epubreader_engine_WavesEngine_readEntry(
        JNIEnv* env,
        jobject /* this */,
        jstring filePath) {
    
    TitanTimer timer("NativeReadEntry");
    
    const char* cPath = env->GetStringUTFChars(filePath, nullptr);
    
    FILE* file = fopen(cPath, "rb");
    if (!file) {
        env->ReleaseStringUTFChars(filePath, cPath);
        return nullptr;
    }

    fseek(file, 0, SEEK_END);
    long size = ftell(file);
    fseek(file, 0, SEEK_SET);

    std::vector<char> buffer(size + 1);
    fread(buffer.data(), 1, size, file);
    buffer[size] = '\0';
    
    fclose(file);
    
    jstring result = env->NewStringUTF(buffer.data());
    
    env->ReleaseStringUTFChars(filePath, cPath);
    return result;
}
