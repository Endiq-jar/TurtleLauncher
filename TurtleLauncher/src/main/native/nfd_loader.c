#include <jni.h>
extern void turtle_install_nfd(JNIEnv *env);
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    turtle_install_nfd(env);
    return JNI_VERSION_1_6;
}
/* The bridge owns its malloc/free pairs; LWJGL's optional allocator must not free these paths. */
JNIEXPORT void JNICALL Java_org_lwjgl_util_nfd_LibNFD_setupMalloc(
    JNIEnv *e, jclass c, jlong m, jlong ca, jlong r, jlong f, jlong a, jlong af) { }
