#import "QuickJsBridge.h"
#include <Foundation/NSObjCRuntime.h>
#include <string.h>
#include <Foundation/Foundation.h>
#include "quickjs.h"

// JS 回调 Native 的 C 函数: NativeBridge.onMetric(tag, durationMs)
static JSValue JsNativeBridge_onMetric(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 2) return JS_UNDEFINED;

    // 通过 ContextQpaque 反查所属的 QuickJsBridge 实例
    QuickJsBridge *bridge = (__bridge QuickJsBridge*)JS_GetContextOpaque(ctx);
    if (!bridge || !bridge.metricDelegate) return JS_UNDEFINED;

    const char *tagStr = JS_ToCString(ctx, argv[0]);
    double duration = 0.0;
    JS_ToFloat64(ctx, &duration, argv[1]);

    if (tagStr) {
        NSString *tag = [NSString stringWithUTF8String:tagStr];
        JS_FreeCString(ctx, tagStr);

        // 派发到主线程通知业务方
        dispatch_async(dispatch_get_main_queue(), ^{
            [bridge.metricDelegate onMetricWithTag:tag durationMs:duration];
        });
    }

    return JS_UNDEFINED;
}

@interface QuickJsBridge () {
    JSRuntime *_rt;
    JSContext *_ctx;
}
@end

@implementation QuickJsBridge

- (instancetype)init {
    self = [super init];
    if (self) {
        [self initQuickJs];
    }
    return self;
}

- (void)initQuickJs {
    _rt = JS_NewRuntime();
    _ctx = JS_NewContext(_rt);

    // 绑定 self 到 ContextOpaque
    JS_SetContextOpaque(_ctx, (__bridge void *)self);

    // 向 JS 注入全局对象 NativeBridge.onMetric
    JSValue global = JS_GetGlobalObject(_ctx);
    JSValue bridgeObj = JS_NewObject(_ctx);
    JS_SetPropertyStr(_ctx, bridgeObj, "onMetric", JS_NewCFunction(_ctx, JsNativeBridge_onMetric, "onMetric", 2));
    JS_SetPropertyStr(_ctx, global, "NativeBridge", bridgeObj);
    JS_FreeValue(_ctx, global);
}

- (nullable NSString *)evaluateScript:(NSString *)script filename:(NSString *)filename {
    if (!_ctx || !script) return nil;

    const char *cScript = [script UTF8String];
    const char *cFilename = filename ? [filename UTF8String] : "<eval>";

    JSValue result = JS_Eval(_ctx, cScript, strlen(cScript), cFilename, JS_EVAL_TYPE_GLOBAL);

    if (JS_IsException(result)) {
        JSValue exc = JS_GetException(_ctx);
        const char *errStr = JS_ToCString(_ctx, exc);
        NSString *error = errStr ? [NSString stringWithUTF8String:errStr] : @"QuickJS Error";
        JS_FreeCString(_ctx, errStr);
        JS_FreeValue(_ctx, exc);
        NSLog(@"[QuickJS Exception] %@", error);
        return [NSString stringWithFormat:@"Error: %@", error];
    }

    const char *outputStr = JS_ToCString(_ctx, result);
    NSString *output = outputStr ? [NSString stringWithUTF8String:outputStr] : @"";
    JS_FreeCString(_ctx, outputStr);
    JS_FreeValue(_ctx, result);
    return output;
}

- (void)dealloc {
    if (_ctx) {
        JS_FreeContext(_ctx);
        _ctx = NULL;
    }
    if (_rt) {
        JS_FreeRuntime(_rt);
        _rt = NULL;
    }
}

@end