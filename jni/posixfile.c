#include "org_dyndns_fules_PosixFile.h"

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <utime.h>

#ifdef __cplusplus
extern "C" {
#endif

//#define DEBUG

#ifdef DEBUG
#   define LOGV(TAG,...) __android_log_print(ANDROID_LOG_VERBOSE, TAG,__VA_ARGS__)
#   define LOGD(TAG,...) __android_log_print(ANDROID_LOG_DEBUG  , TAG,__VA_ARGS__)
#else
#   define LOGV(TAG,...) {}
#   define LOGD(TAG,...) {}
#endif

#define LOGI(TAG,...) __android_log_print(ANDROID_LOG_INFO   , TAG,__VA_ARGS__)
#define LOGW(TAG,...) __android_log_print(ANDROID_LOG_WARN   , TAG,__VA_ARGS__)
#define LOGE(TAG,...) __android_log_print(ANDROID_LOG_ERROR  , TAG,__VA_ARGS__)

static jboolean initialised = 0;

static const char *TAG = "PosixFile";

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    return JNI_VERSION_1_2;
}

jclass cls_IOException;
jclass cls_File;
jclass cls_PosixFile;

jmethodID id_IOException_new;
jmethodID id_File_getPath;

/******************************************************************************
 * helper primitives
 */

static jint throwIOException(JNIEnv *env, const char *fmt, ...) {
	va_list ap;
	char *msg = NULL;
	int res = -1;

	if (cls_IOException && id_IOException_new) {
		va_start(ap, fmt);
		if (vasprintf(&msg, fmt, ap) >= 0) {
			jstring jsMessage = (*env)->NewStringUTF(env, msg);
			res = (*env)->Throw(env, (*env)->NewObject(env, cls_IOException, id_IOException_new, jsMessage));
			(*env)->DeleteLocalRef(env, jsMessage); 
			free(msg);
		}
		va_end(ap);
	}
    return res;
}

typedef struct {
	jstring js;
	const char* s;
} U8String_t;


static int getPath(JNIEnv *env, jobject self, U8String_t *p) {
	p->js = (jstring)((*env)->CallObjectMethod(env, self, id_File_getPath));
	if (p->js) {
		p->s = (const char*)(*env)->GetStringUTFChars(env, p->js, NULL);
		return p->s != NULL;
	}
	p->s = NULL;
	return 0;
}

static void releaseU8String(JNIEnv *env, U8String_t *p) {
	if (p->js) {
		if (p->s) {
			(*env)->ReleaseStringUTFChars(env, p->js, (const jbyte*)p->s);
			p->s = NULL;
		}
		(*env)->DeleteLocalRef(env, p->js);
		p->js = NULL;
	}
}

/******************************************************************************
 * class initialiser
 */

JNIEXPORT void JNICALL Java_org_dyndns_fules_PosixFile_init(JNIEnv *env, jclass clazz) {
    if (initialised)
        return;

    cls_IOException = (*env)->FindClass(env, "java/io/IOException");
    cls_File = (*env)->FindClass(env, "java/io/File");
    cls_PosixFile = (*env)->FindClass(env, "org/dyndns/fules/PosixFile");

    id_IOException_new = (*env)->GetMethodID(env, cls_IOException, "<init>", "(Ljava/lang/String;)V");
    id_File_getPath = (*env)->GetMethodID(env, cls_File, "getPath", "()Ljava/lang/String;");

    initialised = 1;
}

/******************************************************************************
 * native methods
 */

JNIEXPORT jlong JNICALL Java_org_dyndns_fules_PosixFile_lastAccessed(JNIEnv *env, jobject self) {
	jlong result = 0;
	U8String_t path;
	struct stat st;

	if (getPath(env, self, &path) && !stat(path.s, &st))
		result = 1000L * (jlong)st.st_atime;
	releaseU8String(env, &path);
	return result;
}

JNIEXPORT jboolean JNICALL Java_org_dyndns_fules_PosixFile_setLastAccessed(JNIEnv *env, jobject self, jlong t) {
	jboolean result = 0;
	U8String_t path;
	struct stat st;
	struct utimbuf u;
	
	if (getPath(env, self, &path) && !stat(path.s, &st)) {
		u.actime = t / 1000;
		u.modtime = st.st_mtime;
		if (!utime(path.s, &u))
			result = 1;
	}
	releaseU8String(env, &path);
	return result;
}

JNIEXPORT jlong JNICALL Java_org_dyndns_fules_PosixFile_getDeviceId(JNIEnv *env, jobject self) {
	jlong result = 0;
	U8String_t path;
	struct stat st;

	if (getPath(env, self, &path) && !stat(path.s, &st))
		result = (jlong)st.st_dev;
	releaseU8String(env, &path);
	return result;
}

JNIEXPORT jint JNICALL Java_org_dyndns_fules_PosixFile_getPermissions(JNIEnv *env, jobject self) {
	jint result = 0;
	U8String_t path;
	struct stat st;

	if (getPath(env, self, &path) && !stat(path.s, &st))
		result = (jint)(st.st_mode & 07777);
	releaseU8String(env, &path);
	return result;
}

JNIEXPORT jboolean JNICALL Java_org_dyndns_fules_PosixFile_setPermissions(JNIEnv *env, jobject self, jint p) {
	jboolean result = 0;
	U8String_t path;
	
	if (getPath(env, self, &path) && !chmod(path.s, p))
		result = 1;
	releaseU8String(env, &path);
	return result;
}

JNIEXPORT jlong JNICALL Java_org_dyndns_fules_PosixFile_getOwner(JNIEnv *env, jobject self) {
	jlong result = 0;
	U8String_t path;
	struct stat st;

	if (getPath(env, self, &path) && !stat(path.s, &st))
		result = (jlong)st.st_uid;
	releaseU8String(env, &path);
	return result;
}

JNIEXPORT jlong JNICALL Java_org_dyndns_fules_PosixFile_getGroup(JNIEnv *env, jobject self) {
	jlong result = 0;
	U8String_t path;
	struct stat st;

	if (getPath(env, self, &path) && !stat(path.s, &st))
		result = (jlong)st.st_gid;
	releaseU8String(env, &path);
	return result;
}

JNIEXPORT jboolean JNICALL Java_org_dyndns_fules_PosixFile_setOwnerAndGroup(JNIEnv *env, jobject self, jlong u, jlong g) {
	jboolean result = 0;
	U8String_t path;
	
	if (getPath(env, self, &path) && !chown(path.s, u, g))
		result = 1;
	releaseU8String(env, &path);
}

JNIEXPORT jboolean JNICALL Java_org_dyndns_fules_PosixFile_isLink(JNIEnv *env, jobject self) {
	jboolean result = 0;
	U8String_t path;
	struct stat st;
	
	if (getPath(env, self, &path) && !stat(path.s, &st))
		result = S_ISLNK(st.st_mode);
	releaseU8String(env, &path);
}

JNIEXPORT jboolean JNICALL Java_org_dyndns_fules_PosixFile_symlink(JNIEnv *env, jobject self, jstring f) {
	jboolean result = 0;
	U8String_t path;
	struct stat st;
	
	if (getPath(env, self, &path)) {
		const jbyte *from = (*env)->GetStringUTFChars(env, f, NULL);
		if (f && !symlink((const char*)f, path.s))
			result = 1;
		(*env)->ReleaseStringUTFChars(env, f, from);
	}
	releaseU8String(env, &path);
	return result;
}

JNIEXPORT jstring JNICALL Java_org_dyndns_fules_PosixFile_getLink(JNIEnv *env, jobject self) {
	jstring result = NULL;
	U8String_t path;


	if (getPath(env, self, &path)) {
		char buf[PATH_MAX + 1];
		ssize_t n = readlink(path.s, buf, PATH_MAX);
		if (n >= 0) {
			buf[n] = '\0';
			result = (*env)->NewStringUTF(env, buf);
		}
	}
	releaseU8String(env, &path);
	return result;
}

#ifdef __cplusplus
}
#endif

