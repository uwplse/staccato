JAVA_OPTS="$JAVA_OPTS"" -Xbootclasspath/p:/opt/tomcat/tomcat7/phosphor.jar:/opt/tomcat/tomcat7/staccato.jar -javaagent:/opt/tomcat/tomcat7/phosphor.jar -Xmx256m  -Dstaccato.lin-groups='LocaleLanguage,LocaleCountry,LocaleVariant;mail.smtp.username,mail.smtp.password' -Dstaccato.ignored-props=SettingsChanged,LastScanned -Dstaccato.config-as-taint=false -Dstaccato.record-mem=true -Dstaccato.mem-file=/tmp/staccato.mem"
# -agentpath:/opt/tomcat/tomcat7/libjprofilerti.so=port=8849,nowait"
JAVA_HOME="/opt/jvm/jvm-inst"
