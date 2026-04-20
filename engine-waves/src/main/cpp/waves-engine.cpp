#include <jni.h>
#include <string>
#include <android/log.h>
#include "TitanLog.h"
#include "WebnovelScraper.h"

#define TAG "WavesEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jboolean JNICALL
Java_com_epubreader_engine_WavesEngine_initializeEngine(
        JNIEnv* env,
        jobject /* this */) {
    
    LOGI("Blue Waves Engine V2: Initializing Native Core...");
    
    // Future: Vulkan / GLES initialization
    // Future: PDF Reflow engine setup
    
    return JNI_TRUE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_epubreader_engine_WavesEngine_searchVolumetric(
        JNIEnv* env,
        jobject thiz,
        jstring query) {
    
    TitanTimer timer("JNI_searchVolumetric");
    
    // 1. Prepare the fetcher callback (Native to Kotlin)
    auto fetcher = [&](const std::string& url) -> std::string {
        jclass clazz = env->GetObjectClass(thiz);
        jmethodID methodId = env->GetMethodID(clazz, "fetchHtml", "(Ljava/lang/String;)Ljava/lang/String;");
        
        jstring jUrl = env->NewStringUTF(url.c_str());
        jstring jHtml = (jstring)env->CallObjectMethod(thiz, methodId, jUrl);
        
        if (jHtml == nullptr) return "";
        
        const char* htmlChars = env->GetStringUTFChars(jHtml, nullptr);
        std::string html(htmlChars);
        env->ReleaseStringUTFChars(jHtml, htmlChars);
        
        return html;
    };

    // 2. Execute the scraper
    WebnovelScraper scraper(fetcher);
    const char* queryChars = env->GetStringUTFChars(query, nullptr);
    auto nativeResults = scraper.search(queryChars);
    env->ReleaseStringUTFChars(query, queryChars);

    // 3. Convert to Java ScraperResultV2 array
    jclass resultClass = env->FindClass("com/epubreader/engine/ScraperResultV2");
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    
    jobjectArray jResults = env->NewObjectArray(nativeResults.size(), resultClass, nullptr);

    for (size_t i = 0; i < nativeResults.size(); i++) {
        jstring title = env->NewStringUTF(nativeResults[i].title.c_str());
        jstring url = env->NewStringUTF(nativeResults[i].url.c_str());
        jstring cover = env->NewStringUTF(nativeResults[i].coverUrl.c_str());
        jstring source = env->NewStringUTF(nativeResults[i].source.c_str());

        jobject resObj = env->NewObject(resultClass, constructor, title, url, cover, source);
        env->SetObjectArrayElement(jResults, i, resObj);
    }

    return jResults;
}

/**
 * Returns the current version of the native engine.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_epubreader_engine_WavesEngine_getEngineVersion(
        JNIEnv* env,
        jobject /* this */) {
    std::string version = "2.0.0-pro (Native)";
    return env->NewStringUTF(version.c_str());
}
