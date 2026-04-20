#include <jni.h>
#include <string>
#include <vector>
#include "TitanLog.h"

/**
 * MockScraper (V2 Demo)
 * 
 * Simulates search results to verify the Volumetric UI pipeline.
 */

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_epubreader_engine_WavesEngine_mockSearch(
        JNIEnv* env,
        jobject /* this */,
        jstring query) {
    
    TitanTimer timer("NativeMockSearch");
    
    // Create 5 mock results
    const int count = 5;
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray results = env->NewObjectArray(count, stringClass, nullptr);

    const char* mockTitles[] = {
        "The Shadow of Titan",
        "Liquid Memories",
        "Blue Waves: Vol I",
        "Atomic Architecture",
        "The Lego Engine"
    };

    for (int i = 0; i < count; i++) {
        env->SetObjectArrayElement(results, i, env->NewStringUTF(mockTitles[i]));
    }

    return results;
}
