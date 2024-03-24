
OUT = gerac.jar
JAVAC = javac
JAR = jar
SRC_DIR = src
CLASSES_DIR = out
MAIN = typesafeschwalbe.gerac.cli.Main

rwildcard = $(foreach d,$(wildcard $(1:=/*)),$(call rwildcard,$d,$2) $(filter $(subst *,%,$2),$d))

SOURCES = $(call rwildcard, $(SRC_DIR), *.java)

$(OUT): $(SOURCES)
	$(JAVAC) $(SOURCES) -d $(CLASSES_DIR)
	cd $(CLASSES_DIR); $(JAR) -cvef $(MAIN) ../$(OUT) *

clean:
ifeq ($(OS), Windows_NT)
	rmdir /s /q $(CLASSES_DIR)
	del /f /q $(OUT)
else
	rm -rf $(CLASSES_DIR)
	rm -f $(OUT)
endif