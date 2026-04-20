#ifndef TITAN_ENGINE_LOG_H
#define TITAN_ENGINE_LOG_H

#include <android/log.h>
#include <chrono>
#include <cstdarg>

/**
 * Performance Timer for Titan Engine
 */
class TitanTimer {
public:
    TitanTimer(const char* name) : name(name), start(std::chrono::high_resolution_clock::now()) {}
    ~TitanTimer() {
        auto end = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
        __android_log_print(ANDROID_LOG_INFO, "TITAN-PERF", "[PERF] %s took %lld ms", name, (long long)duration);
    }
private:
    const char* name;
    std::chrono::time_point<std::chrono::high_resolution_clock> start;
};

/**
 * TitanLog: A unified logging bridge for the native engine.
 */
class TitanLog {
public:
    static void i(const char* tag, const char* fmt, ...) {
        va_list args;
        va_start(args, fmt);
        __android_log_vprint(ANDROID_LOG_INFO, tag, fmt, args);
        va_end(args);
    }
    static void d(const char* tag, const char* fmt, ...) {
        va_list args;
        va_start(args, fmt);
        __android_log_vprint(ANDROID_LOG_DEBUG, tag, fmt, args);
        va_end(args);
    }
    static void w(const char* tag, const char* fmt, ...) {
        va_list args;
        va_start(args, fmt);
        __android_log_vprint(ANDROID_LOG_WARN, tag, fmt, args);
        va_end(args);
    }
    static void e(const char* tag, const char* fmt, ...) {
        va_list args;
        va_start(args, fmt);
        __android_log_vprint(ANDROID_LOG_ERROR, tag, fmt, args);
        va_end(args);
    }
};

#define TITAN_TAG "TITAN-ENGINE"
#define TLOGI(...) __android_log_print(ANDROID_LOG_INFO, TITAN_TAG, __VA_ARGS__)
#define TLOGE(...) __android_log_print(ANDROID_LOG_ERROR, TITAN_TAG, __VA_ARGS__)
#define TLOGW(...) __android_log_print(ANDROID_LOG_WARN, TITAN_TAG, __VA_ARGS__)

#endif // TITAN_ENGINE_LOG_H
