#include <jni.h>
#include <string>
#include <vector>
#include "TitanLog.h"
#include <memory>
#include "IEngineComponent.h"

class LiquidLayoutEngine : public ILayoutEngine {
public:
    void reflow(const std::string& content, float width) override {
        // Layout logic (moves here in Phase 4)
    }
};

/**
 * Blue Waves Liquid Renderer - Core Engine
 */

enum class ElementType {
    TEXT,
    IMAGE,
    HEADER
};

struct NativeElement {
    ElementType type;
    std::string content;
    std::string style; // JSON or custom style string
    float width;
    float height;
    float x;
    float y;
};

struct NativeLine {
    std::string text;
    float y;
    float width;
};

class ChapterLayout {
public:
    std::vector<NativeLine> lines;
    float totalHeight = 0;
    
    void clear() {
        lines.clear();
        totalHeight = 0;
    }
};

static ChapterLayout currentLayout;

extern "C" JNIEXPORT void JNICALL
Java_com_epubreader_engine_WavesEngine_layoutChapter(
        JNIEnv* env,
        jobject /* this */,
        jstring rawContent,
        jfloat screenWidth,
        jfloat fontSize) {
    
    TitanTimer timer("NativeLayoutChapter");

    const char* cContent = env->GetStringUTFChars(rawContent, nullptr);
    std::string content(cContent);
    
    currentLayout.clear();
    
    // Simple Greedy Wrap Algorithm (Titan Phase 3.1)
    // We assume an average char width for now; refined in 3.2
    float avgCharWidth = fontSize * 0.55f;
    int maxCharsPerLine = static_cast<int>(screenWidth / avgCharWidth);
    
    size_t start = 0;
    float currentY = 0;
    float lineHeight = fontSize * 1.5f;

    while (start < content.length()) {
        size_t end = start + maxCharsPerLine;
        if (end >= content.length()) {
            end = content.length();
        } else {
            // Find last space to avoid breaking words
            size_t lastSpace = content.find_last_of(' ', end);
            if (lastSpace != std::string::npos && lastSpace > start) {
                end = lastSpace;
            }
        }

        NativeLine line;
        line.text = content.substr(start, end - start);
        line.y = currentY;
        line.width = line.text.length() * avgCharWidth;
        
        currentLayout.lines.push_back(line);
        currentY += lineHeight;
        start = end + 1; // Skip the space
    }
    
    currentLayout.totalHeight = currentY;
    
    TLOGI("[LAYOUT] Chapter Reflowed: %d lines generated. Total Height: %.2fpx", 
          (int)currentLayout.lines.size(), currentY);
    
    env->ReleaseStringUTFChars(rawContent, cContent);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_epubreader_engine_WavesEngine_getLineText(
        JNIEnv* env,
        jobject /* this */,
        jint index) {
    if (index >= 0 && index < currentLayout.lines.size()) {
        return env->NewStringUTF(currentLayout.lines[index].text.c_str());
    }
    return nullptr;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_epubreader_engine_WavesEngine_getLineY(
        JNIEnv* env,
        jobject /* this */,
        jint index) {
    if (index >= 0 && index < currentLayout.lines.size()) {
        return currentLayout.lines[index].y;
    }
    return 0.0f;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_epubreader_engine_WavesEngine_getLineCount(
        JNIEnv* env,
        jobject /* this */) {
    return static_cast<jint>(currentLayout.lines.size());
}
