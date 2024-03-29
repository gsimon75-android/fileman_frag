/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class org_dyndns_fules_PosixFile */

#ifndef _Included_org_dyndns_fules_PosixFile
#define _Included_org_dyndns_fules_PosixFile
#ifdef __cplusplus
extern "C" {
#endif
#undef org_dyndns_fules_PosixFile_serialVersionUID
#define org_dyndns_fules_PosixFile_serialVersionUID 301077366599181567LL
/*
 * Class:     org_dyndns_fules_PosixFile
 * Method:    lastAccessed
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_org_dyndns_fules_PosixFile_lastAccessed
  (JNIEnv *, jobject);

/*
 * Class:     org_dyndns_fules_PosixFile
 * Method:    setLastAccessed
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_org_dyndns_fules_PosixFile_setLastAccessed
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_dyndns_fules_PosixFile
 * Method:    getDeviceId
 * Signature: ()I
 */
JNIEXPORT jlong JNICALL Java_org_dyndns_fules_PosixFile_getDeviceId
  (JNIEnv *, jobject);

/*
 * Class:     org_dyndns_fules_PosixFile
 * Method:    getPermissions
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_dyndns_fules_PosixFile_getPermissions
  (JNIEnv *, jobject);

/*
 * Class:     org_dyndns_fules_PosixFile
 * Method:    setPermissions
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL Java_org_dyndns_fules_PosixFile_setPermissions
  (JNIEnv *, jobject, jint);

/*
 * Class:     org_dyndns_fules_PosixFile
 * Method:    getOwner
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_org_dyndns_fules_PosixFile_getOwner
  (JNIEnv *, jobject);

/*
 * Class:     org_dyndns_fules_PosixFile
 * Method:    getGroup
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_org_dyndns_fules_PosixFile_getGroup
  (JNIEnv *, jobject);

/*
 * Class:     org_dyndns_fules_PosixFile
 * Method:    setOwnerAndGroup
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL Java_org_dyndns_fules_PosixFile_setOwnerAndGroup
  (JNIEnv *, jobject, jlong, jlong);

/*
 * Class:     org_dyndns_fules_PosixFile
 * Method:    isLink
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_org_dyndns_fules_PosixFile_isLink
  (JNIEnv *, jobject);

/*
 * Class:     org_dyndns_fules_PosixFile
 * Method:    symlink
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_org_dyndns_fules_PosixFile_symlink
  (JNIEnv *, jobject, jstring);

/*
 * Class:     org_dyndns_fules_PosixFile
 * Method:    getLink
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_dyndns_fules_PosixFile_getLink
  (JNIEnv *, jobject);

#ifdef __cplusplus
}
#endif
#endif
