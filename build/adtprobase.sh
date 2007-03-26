#\!/bin/sh
#
# ADTPro - *nix startup shell script
#
# You can set two variables here:
#   1. $MY_JAVA_HOME - to pick a particular java to run under
#   2. $ADTPRO_HOME - to say where you installed ADTPro
#
# e.g.
#
# export MY_JAVA_HOME=/usr/local/java/bin/ (need final slash)
# export ADTPRO_HOME=~/myuser/adtpro/  (need final slash)
#
@start /min $MY_JAVA_HOMEjava -jar $ADTPRO_HOME"%ADTPRO_VERSION%
"$MY_JAVA_HOME"java -jar "$ADTPRO_HOME"%ADTPRO_VERSION% &