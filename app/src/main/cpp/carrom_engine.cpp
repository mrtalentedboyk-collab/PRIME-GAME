#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <vector>
#include <cmath>

#define LOG_TAG "PrimeGameCPP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

struct PointF { float x, y; };
struct Coin { PointF pos; bool isMine; };

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_primegame_assistant_CarromEngine_processFrameNative(JNIEnv *env, jobject thiz, jobject bitmap, jint width, jint height, jboolean isAutoPlayOn) {
    AndroidBitmapInfo info;
    void* pixels;
    
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return nullptr;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return nullptr;

    uint32_t* pixelBuffer = (uint32_t*)pixels;
    
    float boardMargin = 45.0f;
    float boardSize = (float)width - (boardMargin * 2.0f);
    float boardTop = ((float)height - (float)width) / 2.0f + boardMargin;
    
    PointF pockets[4] = {
        {boardMargin + 45.0f, boardTop + 45.0f},
        {width - boardMargin - 45.0f, boardTop + 45.0f},
        {boardMargin + 45.0f, boardTop + boardSize - 45.0f},
        {width - boardMargin - 45.0f, boardTop + boardSize - 45.0f}
    };

    std::vector<Coin> coins;
    PointF strikerPt = { (float)width / 2.0f, boardTop + boardSize - (boardSize * 0.15f) };
    bool strikerFound = false;

    int startY = std::max(0, (int)boardTop);
    int endY = std::min((int)height, (int)(boardTop + boardSize));
    int startX = std::max(0, (int)boardMargin);
    int endX = std::min((int)width, (int)(width - boardMargin));

    for (int y = startY; y < endY; y += 4) {
        int rowOffset = y * info.stride / 4;
        for (int x = startX; x < endX; x += 4) {
            uint32_t p = pixelBuffer[rowOffset + x];
            int r = (p) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = (p >> 16) & 0xFF;

            bool isWhite = (r > 190 && g > 190 && b > 190); // আপনার নিজস্ব গুটি ও স্ট্রাইকার
            bool isBlack = (r >= 15 && r <= 70 && g >= 15 && g <= 70 && b >= 15 && b <= 70);

            if (isWhite || isBlack) {
                float realX = (float)x;
                float realY = (float)y;
                
                // স্ট্রাইকার ডিটেক্ট করা (বোর্ডের নিচের অংশে)
                if (isWhite && std::abs(realY - strikerPt.y) < 120.0f && realY > (boardTop + boardSize * 0.7f)) {
                    if (!strikerFound || realY > strikerPt.y) {
                        strikerPt = {realX, realY};
                        strikerFound = true;
                    }
                } else if (isWhite || isBlack) {
                    bool exists = false;
                    for (auto& c : coins) {
                        if (std::hypot(c.pos.x - realX, c.pos.y - realY) < 25.0f) {
                            exists = true; break;
                        }
                    }
                    if (!exists) coins.push_back({{realX, realY}, isWhite});
                }
            }
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);

    float aimAngle = 0.0f;
    Coin bestCoin = {{-1.0f, -1.0f}, false};
    PointF bestPocket = {-1.0f, -1.0f};
    bool isBotAiming = false;

    // প্রো-বট লজিক: শুধুমাত্র আপনার (White) গুটিগুলো পকেটে ফেলার জন্য ক্যালকুলেট করবে
    if (isAutoPlayOn) {
        float bestScore = 999999.0f;
        for (auto& coin : coins) {
            if (!coin.isMine) continue; // অপোনেন্টের কালো গুটি বাদ দিয়ে শুধু নিজের গুটি ধরবে
            
            for (auto& pocket : pockets) {
                float anglePocketToCoin = std::atan2(coin.pos.y - pocket.y, coin.pos.x - pocket.x);
                float ghostX = coin.pos.x + std::cos(anglePocketToCoin) * 55.0f;
                float ghostY = coin.pos.y + std::sin(anglePocketToCoin) * 55.0f;
                
                float angleStrikerToGhost = std::atan2(ghostY - strikerPt.y, ghostX - strikerPt.x);
                float cutAngle = std::abs(anglePocketToCoin - angleStrikerToGhost);
                
                if (cutAngle < bestScore) {
                    bestScore = cutAngle;
                    bestCoin = coin;
                    bestPocket = pocket;
                    aimAngle = angleStrikerToGhost;
                    isBotAiming = true;
                }
            }
        }
    }

    int totalElements = 9 + (coins.size() * 3);
    jfloatArray result = env->NewFloatArray(totalElements);
    jfloat* resBuf = env->GetFloatArrayElements(result, nullptr);

    resBuf[0] = strikerPt.x; resBuf[1] = strikerPt.y;
    resBuf[2] = aimAngle;
    resBuf[3] = isBotAiming ? 1.0f : 0.0f;
    resBuf[4] = bestCoin.pos.x; resBuf[5] = bestCoin.pos.y;
    resBuf[6] = bestPocket.x; resBuf[7] = bestPocket.y;
    resBuf[8] = (float)coins.size();
    
    int index = 9;
    for(auto& c : coins) {
        resBuf[index++] = c.pos.x;
        resBuf[index++] = c.pos.y;
        resBuf[index++] = c.isMine ? 1.0f : 0.0f;
    }

    env->ReleaseFloatArrayElements(result, resBuf, 0);
    return result;
}
