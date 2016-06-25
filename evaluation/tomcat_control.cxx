#include<string>
#include<unistd.h>
#include<libgen.h>
#include<iostream>
#include<string.h>

int main(int argc, char **argv) {
  if(argc < 2) {
	return 1;
  }
  std::string mode(argv[1]);
  if(mode == std::string("start")) {
	execlp("bash", "bash", "/etc/init.d/tomcat7", "start", NULL);
  } else if(mode == std::string("stop")) {
	std::string kill_command(dirname(argv[0]));
	kill_command += "/kill_tomcat.sh";
	execlp("bash", "bash", kill_command.c_str(), NULL);
  } else if(mode == std::string("dump")) {
	std::string dump_command(dirname(argv[0]));
	dump_command += "/tomcat_dump.sh";
	execlp("bash", "bash", dump_command.c_str(), NULL);
  }
  return -1;
}
