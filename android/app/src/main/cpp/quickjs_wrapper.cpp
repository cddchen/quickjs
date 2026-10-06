// JNI 胶水层与双向桥接
// 1. Native 调 JS: evaluateScript
// 2. JS 调 Native: 注册全局 NativeBridge.onMetric(tag, durationMs)，在 JS 执行完毕时通知 Native 侧
#include <jni.h>
#include <string>
#include "quickjs/quickjs.h"
#include <android/log.h>
#include <cstring>

#define TAG "QuickJsJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

struct EngineContext {
    JavaVM* jvm;
    jobject listenerGlobalRef;
    JSRuntime* rt;
    JSContext* ctx;
};

// JS 回调 Native 的 C 绑定函数: NativeBridge.onMetric(tag, durationMs)
static JSValue JsNativeBridge_onMetric(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 2) return JS_UNDEFINED;
    
    auto* engine = static_cast<EngineContext*>(JS_GetContextOpaque(ctx));
    if (!engine || !engine->listenerGlobalRef) return JS_UNDEFINED;

    const char* tag = JS_ToCString(ctx, argv[0]);
    double duration = 0.0;
    JS_ToFloat64(ctx, &duration, argv[1]);

    // 跨线程/JNI 回调 Java 接口
    JNIEnv* env = nullptr;
    if (engine->jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_OK) {
        jclass clazz = env->GetObjectClass(engine->listenerGlobalRef);
        jmethodID method = env->GetMethodID(clazz, "onMetric", "(Ljava/lang/String;D)V");
        if (method) {
            jstring jTag = env->NewStringUTF(tag);
            env->CallVoidMethod(engine->listenerGlobalRef, method, jTag, duration);
            env->DeleteLocalRef(jTag);
        }
        env->DeleteLocalRef(clazz);
    }
    JS_FreeCString(ctx, tag);
    return JS_UNDEFINED;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_latex_engine_QuickJsEngine_nativeInit(JNIEnv* env, jobject thiz, jobject listener) {
    auto* engine = new EngineContext();
    env->GetJavaVM(&engine->jvm);
    engine->listenerGlobalRef = listener ? env->NewGlobalRef(listener) : nullptr;
    
    engine->rt = JS_NewRuntime();
    engine->ctx = JS_NewContext(engine->rt);
    JS_SetContextOpaque(engine->ctx, engine);

    // 向 JS 全局注入 NativeBridge 对象和方法
    JSValue global = JS_GetGlobalObject(engine->ctx);
    JSValue bridge = JS_NewObject(engine->ctx);
    JS_SetPropertyStr(
        engine->ctx, 
        bridge, 
        "onMetric", 
        JS_NewCFunction(
            engine->ctx, 
            JsNativeBridge_onMetric, 
            "onMetric", 
            2
        )
    );
    JS_SetPropertyStr(engine->ctx, global, "NativeBridge", bridge);
    JS_FreeValue(engine->ctx, global);

    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_latex_engine_QuickJsEngine_nativeEval(JNIEnv* env, jobject thiz, jlong handle, jstring code) {
    auto* engine = reinterpret_cast<EngineContext*>(handle);
    const char* script = env->GetStringUTFChars(code, nullptr);

    JSValue result = JS_Eval(engine->ctx, script, strlen(script), "<eval>", JS_EVAL_TYPE_GLOBAL);
    env->ReleaseStringUTFChars(code, script);

    if (JS_IsException(result)) {
        JSValue exc = JS_GetException(engine->ctx);
        const char* err = JS_ToCString(engine->ctx, exc);
        JS_FreeValue(engine->ctx, exc);
        jstring jErr = env->NewStringUTF(err ? err : "QuickJS Eval Error");
        JS_FreeCString(engine->ctx, err);
        return jErr;
    }

    const char* output = JS_ToCString(engine->ctx, result);
    jstring jOutput = env->NewStringUTF(output ? output : "");
    JS_FreeCString(engine->ctx, output);
    JS_FreeValue(engine->ctx, result);
    return jOutput;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_latex_engine_QuickJsEngine_nativeRelease(JNIEnv* env, jobject thiz, jlong handle) {
    auto* engine = reinterpret_cast<EngineContext*>(handle);
    if (!engine) return;
    if (engine->listenerGlobalRef) {
        env->DeleteGlobalRef(engine->listenerGlobalRef);
    }
    JS_FreeContext(engine->ctx);
    JS_FreeRuntime(engine->rt);
    delete engine;
}