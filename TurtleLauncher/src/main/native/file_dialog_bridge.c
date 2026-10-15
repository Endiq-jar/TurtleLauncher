/* TurtleLauncher Android/HotSpot file-dialog bridge. GPL-3.0-or-later.
 * JNI registration is signature-checked: LWJGL 3.2 legacy NFD and 3.3+ NFD have different ABIs.
 * Never pass a JNIEnv or local JNI reference between the two VMs.
 */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>

#define PTR(v) ((void *)(intptr_t)(v))
#define LONG(v) ((jlong)(intptr_t)(v))
static JavaVM *android_vm;
static jclass android_bridge;
static jmethodID request_method;
static pthread_mutex_t bind_lock = PTHREAD_MUTEX_INITIALIZER;
typedef struct { char **paths; size_t count; } PathSet;
typedef struct { PathSet *set; size_t index; } PathEnum;
typedef struct { const char *name; const char *spec; } Filter;

static void free_set(PathSet *set) {
    if (!set) return;
    for (size_t i = 0; i < set->count; ++i) free(set->paths[i]);
    free(set->paths);
    free(set);
}

JNIEXPORT void JNICALL Java_com_endiq_turtlelauncher_bridge_NativeFileDialogs_bind(JNIEnv *env, jclass cls) {
    pthread_mutex_lock(&bind_lock);
    (*env)->GetJavaVM(env, &android_vm);
    if (!android_bridge) android_bridge = (*env)->NewGlobalRef(env, cls);
    request_method = (*env)->GetStaticMethodID(env, cls, "request", "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/String;");
    pthread_mutex_unlock(&bind_lock);
}

static char *utf8(JNIEnv *env, jstring str) {
    jclass cls = (*env)->FindClass(env, "java/lang/String");
    jmethodID get_bytes = (*env)->GetMethodID(env, cls, "getBytes", "(Ljava/lang/String;)[B");
    jstring encoding = (*env)->NewStringUTF(env, "UTF-8");
    jbyteArray bytes = (*env)->CallObjectMethod(env, str, get_bytes, encoding);
    if (!bytes || (*env)->ExceptionCheck(env)) return NULL;
    jsize n = (*env)->GetArrayLength(env, bytes);
    char *out = calloc((size_t)n + 1, 1);
    if (out) (*env)->GetByteArrayRegion(env, bytes, 0, n, (jbyte *)out);
    (*env)->DeleteLocalRef(env, bytes);
    (*env)->DeleteLocalRef(env, encoding);
    (*env)->DeleteLocalRef(env, cls);
    return out;
}

static PathSet *request(int mode, const char *title, const char *name, const char *filters) {
    if (!android_vm || !android_bridge || !request_method) return NULL;
    JNIEnv *env = NULL;
    int attached = 0;
    jint status = (*android_vm)->GetEnv(android_vm, (void **)&env, JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        if ((*android_vm)->AttachCurrentThread(android_vm, (void **)&env, NULL) != JNI_OK) return NULL;
        attached = 1;
    } else if (status != JNI_OK) return NULL;
    if ((*env)->PushLocalFrame(env, 32) < 0) {
        (*env)->ExceptionClear(env);
        if (attached) (*android_vm)->DetachCurrentThread(android_vm);
        return NULL;
    }
    jstring jt = title ? (*env)->NewStringUTF(env, title) : NULL;
    jstring jn = name ? (*env)->NewStringUTF(env, name) : NULL;
    jstring jf = filters ? (*env)->NewStringUTF(env, filters) : NULL;
    jobjectArray paths = (*env)->CallStaticObjectMethod(env, android_bridge, request_method, mode, jt, jn, jf);
    PathSet *set = NULL;
    if (!(*env)->ExceptionCheck(env) && paths) {
        jsize count = (*env)->GetArrayLength(env, paths);
        set = calloc(1, sizeof(*set));
        if (set) set->paths = calloc((size_t)count, sizeof(char *));
        if (set && set->paths) {
            for (jsize i = 0; i < count; ++i) {
                jstring path = (*env)->GetObjectArrayElement(env, paths, i);
                set->paths[i] = utf8(env, path);
                (*env)->DeleteLocalRef(env, path);
                if (!set->paths[i] || (*env)->ExceptionCheck(env)) break;
                set->count++;
            }
        }
        if (set && (set->count != (size_t)count || !set->count)) { free_set(set); set = NULL; }
    }
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); free_set(set); set = NULL; }
    (*env)->PopLocalFrame(env, NULL);
    if (attached) (*android_vm)->DetachCurrentThread(android_vm);
    return set;
}

static char *filter_string(const Filter *filters, unsigned count) {
    if (!filters || count > 256) return NULL;
    size_t size = 1;
    for (unsigned i = 0; i < count; ++i) if (filters[i].spec) size += strlen(filters[i].spec) + 1;
    char *out = calloc(size, 1);
    if (!out) return NULL;
    for (unsigned i = 0; i < count; ++i) if (filters[i].spec) { strcat(out, filters[i].spec); strcat(out, ";"); }
    return out;
}
static int single(int mode, char **out, const char *name, const char *filters) {
    if (!out) return 0;
    *out = NULL;
    PathSet *set = request(mode, NULL, name, filters);
    if (!set) return 2;
    *out = set->paths[0]; set->paths[0] = NULL; free_set(set);
    return 1;
}

/* TinyFD owns its result buffer until the next invocation. Per-thread storage avoids cross-thread UAF. */
static pthread_key_t tiny_key;
static pthread_once_t tiny_once = PTHREAD_ONCE_INIT;
static void tiny_init(void) { pthread_key_create(&tiny_key, free); }
static jlong tiny_dialog(int mode, jlong title, jlong name, jint count, jlong patterns) {
    pthread_once(&tiny_once, tiny_init);
    free(pthread_getspecific(tiny_key)); pthread_setspecific(tiny_key, NULL);
    const char **p = PTR(patterns);
    Filter *filters = count > 0 && count <= 256 ? calloc((size_t)count, sizeof(Filter)) : NULL;
    if (filters && p) for (int i = 0; i < count; ++i) filters[i].spec = p[i];
    char *spec = filter_string(filters, filters ? (unsigned)count : 0);
    PathSet *set = request(mode, PTR(title), PTR(name), spec);
    free(spec); free(filters);
    if (!set) return 0;
    size_t size = 1;
    for (size_t i = 0; i < set->count; ++i) size += strlen(set->paths[i]) + 1;
    char *result = calloc(size, 1);
    if (result) for (size_t i = 0; i < set->count; ++i) {
        if (i) strcat(result, "|");
        strcat(result, set->paths[i]);
    }
    free_set(set);
    pthread_setspecific(tiny_key, result);
    return LONG(result);
}
static jlong JNICALL tiny_open(JNIEnv *e, jclass c, jlong t, jlong n, jint count, jlong p, jlong desc, jint multiple) {
    return tiny_dialog(multiple ? 1 : 0, t, n, count, p);
}
static jlong JNICALL tiny_save(JNIEnv *e, jclass c, jlong t, jlong n, jint count, jlong p, jlong desc) {
    return tiny_dialog(2, t, n, count, p);
}
static jlong JNICALL tiny_folder(JNIEnv *e, jclass c, jlong t, jlong n) { return tiny_dialog(3, t, n, 0, 0); }

static jint JNICALL nfd_init(JNIEnv *e, jclass c) { return 1; }
static void JNICALL no_op(JNIEnv *e, jclass c) { }
static void JNICALL nfd_free(JNIEnv *e, jclass c, jlong p) { free(PTR(p)); }
static jlong JNICALL nfd_error(JNIEnv *e, jclass c) { return LONG("Android file picker unavailable or operation cancelled"); }
static jint nfd_dialog(int mode, jlong out, jlong filters, jint count, jlong name) {
    char *spec = filter_string(PTR(filters), (unsigned)count);
    int result;
    if (mode == 1) {
        if (!out) { free(spec); return 0; }
        PathSet *set = request(mode, NULL, PTR(name), spec);
        *(PathSet **)PTR(out) = set; result = set ? 1 : 2;
    } else result = single(mode, PTR(out), PTR(name), spec);
    free(spec); return result;
}
static jint JNICALL nfd_open(JNIEnv *e, jclass c, jlong out, jlong f, jint n, jlong d) { return nfd_dialog(0, out, f, n, d); }
static jint JNICALL nfd_multi(JNIEnv *e, jclass c, jlong out, jlong f, jint n, jlong d) { return nfd_dialog(1, out, f, n, d); }
static jint JNICALL nfd_save(JNIEnv *e, jclass c, jlong out, jlong f, jint n, jlong d, jlong name) { return nfd_dialog(2, out, f, n, name); }
static jint JNICALL nfd_folder(JNIEnv *e, jclass c, jlong out, jlong d) { return nfd_dialog(3, out, 0, 0, d); }
/* First fields of NFD's versioned argument structs; remaining window/button fields are UI hints. */
typedef struct { const Filter *filters; unsigned count; const char *directory; } OpenArgs;
typedef struct { const Filter *filters; unsigned count; const char *directory; const char *name; } SaveArgs;
static jint JNICALL nfd_open_with(JNIEnv *e, jclass c, jlong out, jlong ptr) {
    const OpenArgs *a = PTR(ptr);
    return nfd_dialog(0, out, a ? LONG(a->filters) : 0, a ? (jint)a->count : 0, a ? LONG(a->directory) : 0);
}
static jint JNICALL nfd_multi_with(JNIEnv *e, jclass c, jlong out, jlong ptr) {
    const OpenArgs *a = PTR(ptr);
    return nfd_dialog(1, out, a ? LONG(a->filters) : 0, a ? (jint)a->count : 0, a ? LONG(a->directory) : 0);
}
static jint JNICALL nfd_save_with(JNIEnv *e, jclass c, jlong out, jlong ptr) {
    const SaveArgs *a = PTR(ptr);
    return nfd_dialog(2, out, a ? LONG(a->filters) : 0, a ? (jint)a->count : 0, a ? LONG(a->name) : 0);
}
static jint JNICALL nfd_folder_with(JNIEnv *e, jclass c, jlong out, jlong ptr) {
    return nfd_dialog(3, out, 0, 0, ptr ? LONG(*(const char **)PTR(ptr)) : 0);
}

static jint JNICALL set_count(JNIEnv *e, jclass c, jlong p, jlong out) {
    if (!p || !out) return 0;
    *(unsigned *)PTR(out) = (unsigned)((PathSet *)PTR(p))->count; return 1;
}
static jint JNICALL set_count_array(JNIEnv *e, jclass c, jlong p, jintArray out) {
    if (!p || !out || !(*e)->GetArrayLength(e, out)) return 0;
    jint count = (jint)((PathSet *)PTR(p))->count;
    (*e)->SetIntArrayRegion(e, out, 0, 1, &count); return 1;
}
static jint JNICALL set_path(JNIEnv *e, jclass c, jlong p, jint i, jlong out) {
    PathSet *set = PTR(p);
    if (!out) return 0;
    *(char **)PTR(out) = NULL;
    if (!set || i < 0 || (size_t)i >= set->count) return 0;
    *(char **)PTR(out) = strdup(set->paths[i]);
    return *(char **)PTR(out) ? 1 : 0;
}
static jint JNICALL set_enum(JNIEnv *e, jclass c, jlong p, jlong out) {
    if (!p || !out) return 0;
    PathEnum *it = calloc(1, sizeof(*it)); if (!it) return 0;
    it->set = PTR(p); *(PathEnum **)PTR(out) = it; return 1;
}
static jint JNICALL enum_next(JNIEnv *e, jclass c, jlong p, jlong out) {
    if (!p || !out) return 0;
    PathEnum *it = *(PathEnum **)PTR(p);
    *(char **)PTR(out) = NULL;
    if (!it || it->index >= it->set->count) return 1;
    return set_path(e, c, LONG(it->set), (jint)it->index++, out);
}
static void JNICALL enum_free(JNIEnv *e, jclass c, jlong p) {
    if (p) { free(*(void **)PTR(p)); *(void **)PTR(p) = NULL; }
}
static void JNICALL set_free(JNIEnv *e, jclass c, jlong p) { free_set(PTR(p)); }

/* Legacy NFDPathSet exposes these three fields to Java; preserve its actual layout. */
typedef struct { char *buffer; size_t *indices; size_t count; } LegacySet;
static jint JNICALL legacy_open(JNIEnv *e, jclass c, jlong f, jlong d, jlong out) { return single(0, PTR(out), PTR(d), PTR(f)); }
static jint JNICALL legacy_save(JNIEnv *e, jclass c, jlong f, jlong d, jlong out) { return single(2, PTR(out), PTR(d), PTR(f)); }
static jint JNICALL legacy_folder(JNIEnv *e, jclass c, jlong d, jlong out) { return single(3, PTR(out), PTR(d), NULL); }
static int legacy_pack(PathSet *set, LegacySet *out) {
    size_t size = 0;
    for (size_t i = 0; i < set->count; ++i) size += strlen(set->paths[i]) + 1;
    out->buffer = malloc(size);
    out->indices = calloc(set->count, sizeof(size_t));
    if (!out->buffer || !out->indices) {
        free(out->buffer); free(out->indices); memset(out, 0, sizeof(*out)); return 0;
    }
    size_t offset = 0;
    for (size_t i = 0; i < set->count; ++i) {
        out->indices[i] = offset;
        size_t length = strlen(set->paths[i]) + 1;
        memcpy(out->buffer + offset, set->paths[i], length); offset += length;
    }
    out->count = set->count; return 1;
}
static jint JNICALL legacy_multi(JNIEnv *e, jclass c, jlong f, jlong d, jlong out) {
    if (!out) return 0;
    memset(PTR(out), 0, sizeof(LegacySet));
    PathSet *set = request(1, NULL, PTR(d), PTR(f));
    if (!set) return 2;
    int result = legacy_pack(set, PTR(out)); free_set(set); return result;
}
static jlong JNICALL legacy_count(JNIEnv *e, jclass c, jlong p) { return p ? (jlong)((LegacySet *)PTR(p))->count : 0; }
static jlong JNICALL legacy_path(JNIEnv *e, jclass c, jlong p, jlong i) {
    LegacySet *set = PTR(p);
    return set && i >= 0 && (size_t)i < set->count ? LONG(set->buffer + set->indices[i]) : 0;
}
static void JNICALL legacy_free_set(JNIEnv *e, jclass c, jlong p) {
    if (!p) return;
    LegacySet *set = PTR(p);
    free(set->buffer); free(set->indices); memset(set, 0, sizeof(*set));
}

/* Optional registration supports multiple LWJGL releases without pending NoSuchMethod errors. */
static void reg(JNIEnv *env, jclass cls, const char *name, const char *signature, void *fn) {
    if (!(*env)->GetStaticMethodID(env, cls, name, signature)) { (*env)->ExceptionClear(env); return; }
    JNINativeMethod method = {(char *)name, (char *)signature, fn};
    if ((*env)->RegisterNatives(env, cls, &method, 1) != JNI_OK) (*env)->ExceptionClear(env);
}
void turtle_install_nfd(JNIEnv *env) {
    jclass cls = (*env)->FindClass(env, "org/lwjgl/util/nfd/NativeFileDialog");
    if (!cls) { (*env)->ExceptionClear(env); return; }
    int extended = (*env)->GetStaticMethodID(env, cls, "NFD_Init", "()I") != NULL;
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    reg(env, cls, "nNFD_GetError", "()J", nfd_error);
    if (extended) {
        reg(env, cls, "NFD_Init", "()I", nfd_init);
        reg(env, cls, "NFD_Quit", "()V", no_op);
        reg(env, cls, "NFD_ClearError", "()V", no_op);
        reg(env, cls, "nNFD_OpenDialog", "(JJIJ)I", nfd_open);
        reg(env, cls, "nNFD_OpenDialogMultiple", "(JJIJ)I", nfd_multi);
        reg(env, cls, "nNFD_SaveDialog", "(JJIJJ)I", nfd_save);
        reg(env, cls, "nNFD_PickFolder", "(JJ)I", nfd_folder);
        reg(env, cls, "nNFD_OpenDialog_With", "(JJ)I", nfd_open_with);
        reg(env, cls, "nNFD_OpenDialogMultiple_With", "(JJ)I", nfd_multi_with);
        reg(env, cls, "nNFD_SaveDialog_With", "(JJ)I", nfd_save_with);
        reg(env, cls, "nNFD_PickFolder_With", "(JJ)I", nfd_folder_with);
        reg(env, cls, "nNFD_FreePath", "(J)V", nfd_free);
        reg(env, cls, "nNFD_PathSet_GetCount", "(JJ)I", set_count);
        reg(env, cls, "nNFD_PathSet_GetCount", "(J[I)I", set_count_array);
        reg(env, cls, "nNFD_PathSet_GetPath", "(JIJ)I", set_path);
        reg(env, cls, "nNFD_PathSet_FreePath", "(J)V", nfd_free);
        reg(env, cls, "nNFD_PathSet_GetEnum", "(JJ)I", set_enum);
        reg(env, cls, "nNFD_PathSet_EnumNext", "(JJ)I", enum_next);
        reg(env, cls, "nNFD_PathSet_FreeEnum", "(J)V", enum_free);
        reg(env, cls, "nNFD_PathSet_Free", "(J)V", set_free);
    } else {
        reg(env, cls, "nNFD_OpenDialog", "(JJJ)I", legacy_open);
        reg(env, cls, "nNFD_OpenDialogMultiple", "(JJJ)I", legacy_multi);
        reg(env, cls, "nNFD_SaveDialog", "(JJJ)I", legacy_save);
        reg(env, cls, "nNFD_PickFolder", "(JJ)I", legacy_folder);
        reg(env, cls, "nNFD_PathSet_GetCount", "(J)J", legacy_count);
        reg(env, cls, "nNFD_PathSet_GetPath", "(JJ)J", legacy_path);
        reg(env, cls, "nNFD_PathSet_Free", "(J)V", legacy_free_set);
        reg(env, cls, "nNFD_Free", "(J)V", nfd_free);
    }
    (*env)->DeleteLocalRef(env, cls);
}
JNIEXPORT void JNICALL Java_com_endiq_turtlelauncher_bridge_GameFileDialogs_nativeInstallFileDialogs(JNIEnv *env, jclass cls) {
    jclass tiny = (*env)->FindClass(env, "org/lwjgl/util/tinyfd/TinyFileDialogs");
    if (!tiny) { (*env)->ExceptionClear(env); return; }
    reg(env, tiny, "ntinyfd_openFileDialog", "(JJIJJI)J", tiny_open);
    reg(env, tiny, "ntinyfd_saveFileDialog", "(JJIJJ)J", tiny_save);
    reg(env, tiny, "ntinyfd_selectFolderDialog", "(JJ)J", tiny_folder);
    (*env)->DeleteLocalRef(env, tiny);
}
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) { return JNI_VERSION_1_6; }
