#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@protocol QuickJsMetricDelegate <NSObject>
/// JS 侧 NativeBridge.onMetric 反向回调至 Native
- (void)onMetricWithTag:(NSString *)tag durationMs:(double)durationMs;
@end

@interface QuickJsBridge : NSObject

@property (nonatomic, weak, nullable) id<QuickJsMetricDelegate> metricDelegate;

/// 在 QuickJS 全局沙箱中同步求值
- (nullable NSString *)evaluateScript:(NSString *)script filename:(NSString *)filename;

@end

NS_ASSUME_NONNULL_END