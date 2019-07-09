#!/bin/bash

adb logcat -s 'AndroidRuntime:*' 'fileman:*' | tee q
