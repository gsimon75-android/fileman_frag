#!/bin/bash

ant debug && ant installd && adb logcat -c && adb shell am start -d 'file:///mnt/sdcard' org.dyndns.fules.filemanager/.FileManagerActivity && ./log_all.sh 
