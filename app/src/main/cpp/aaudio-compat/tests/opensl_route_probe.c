// SPDX-License-Identifier: Apache-2.0
// Standalone Android shell probe; not part of the LightningDSP APK.
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <math.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

enum { frames = 960 };
static short pcm[2 * frames];

static void refill(SLAndroidSimpleBufferQueueItf queue, void *context) {
    (void)context;
    (*queue)->Enqueue(queue, pcm, sizeof(pcm));
}

int main(int argc, char **argv) {
    if (argc != 2 || (strcmp(argv[1], "latency-effects") && strcmp(argv[1], "none"))) {
        fprintf(stderr, "Usage: %s latency-effects|none\n", argv[0]);
        return 2;
    }
    const SLuint32 mode = !strcmp(argv[1], "none") ? SL_ANDROID_PERFORMANCE_NONE :
        SL_ANDROID_PERFORMANCE_LATENCY_EFFECTS;
    SLObjectItf engine = NULL, mix = NULL, player = NULL;
    SLEngineItf api;
    SLPlayItf play;
    SLAndroidConfigurationItf config;
    SLAndroidSimpleBufferQueueItf queue;
    int code = 1;
    SLresult status;
#define CHECK(call) do { status = (call); if (status != SL_RESULT_SUCCESS) { \
    fprintf(stderr, "%s: %u\n", #call, status); goto cleanup; } } while (0)
    CHECK(slCreateEngine(&engine, 0, NULL, 0, NULL, NULL));
    CHECK((*engine)->Realize(engine, SL_BOOLEAN_FALSE));
    CHECK((*engine)->GetInterface(engine, SL_IID_ENGINE, &api));
    CHECK((*api)->CreateOutputMix(api, &mix, 0, NULL, NULL));
    CHECK((*mix)->Realize(mix, SL_BOOLEAN_FALSE));
    SLDataLocator_AndroidSimpleBufferQueue locator = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2};
    SLDataFormat_PCM format = {SL_DATAFORMAT_PCM, 2, SL_SAMPLINGRATE_48, SL_PCMSAMPLEFORMAT_FIXED_16,
        SL_PCMSAMPLEFORMAT_FIXED_16, SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT, SL_BYTEORDER_LITTLEENDIAN};
    SLDataSource source = {&locator, &format};
    SLDataLocator_OutputMix destination = {SL_DATALOCATOR_OUTPUTMIX, mix};
    SLDataSink sink = {&destination, NULL};
    const SLInterfaceID ids[] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE, SL_IID_ANDROIDCONFIGURATION};
    const SLboolean required[] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
    CHECK((*api)->CreateAudioPlayer(api, &player, &source, &sink, 2, ids, required));
    CHECK((*player)->GetInterface(player, SL_IID_ANDROIDCONFIGURATION, &config));
    CHECK((*config)->SetConfiguration(config, SL_ANDROID_KEY_PERFORMANCE_MODE, &mode, sizeof(mode)));
    CHECK((*player)->Realize(player, SL_BOOLEAN_FALSE));
    SLuint32 actual = 0xffffffff, size = sizeof(actual);
    status = (*config)->GetConfiguration(config, SL_ANDROID_KEY_PERFORMANCE_MODE, &size, &actual);
    printf("pid=%d requested=%u readback_status=%u actual=%u\n", getpid(), mode, status, actual);
    fflush(stdout);
    CHECK((*player)->GetInterface(player, SL_IID_PLAY, &play));
    CHECK((*player)->GetInterface(player, SL_IID_ANDROIDSIMPLEBUFFERQUEUE, &queue));
    // Quiet, continuous 400 Hz tone with an integer number of cycles per buffer.
    for (int i = 0; i < frames; ++i)
        pcm[2 * i] = pcm[2 * i + 1] = (short)(240.0 * sin(i * 6.283185307179586 / 120.0));
    CHECK((*queue)->RegisterCallback(queue, refill, NULL));
    CHECK((*queue)->Enqueue(queue, pcm, sizeof(pcm)));
    CHECK((*queue)->Enqueue(queue, pcm, sizeof(pcm)));
    CHECK((*play)->SetPlayState(play, SL_PLAYSTATE_PLAYING));
    sleep(6);
    CHECK((*play)->SetPlayState(play, SL_PLAYSTATE_STOPPED));
    code = 0;
cleanup:
    if (player) (*player)->Destroy(player);
    if (mix) (*mix)->Destroy(mix);
    if (engine) (*engine)->Destroy(engine);
    return code;
}
