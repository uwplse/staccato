#include<string>
#include<unistd.h>
#include<libgen.h>
#include<iostream>
#include<string.h>

int main(int argc, char **argv) {
  if(argc < 2) {
	return 1;
  }
  std::string script(dirname(argv[0]));
  
  std::string mode(argv[1]);
  if(mode == "jforum") {
	script.append("/jforum/deploy_jforum.sh");
  } else if(mode == "subsonic") {
	script.append("/subsonic/deploy_subsonic.sh");
  } else {
	return -1;
  }
  size_t len = argc + 1;
  char** args = new char*[len];
  args[0] = "bash";
  args[1] = strdup(script.c_str());
  for(int i = 2; i < argc; i++) {
	args[i] = argv[i];
  }
  args[argc] = NULL;
  execvp("bash", args);
  return -1;
}
