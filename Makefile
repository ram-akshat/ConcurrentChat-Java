SRC_DIR   = src
OUT_DIR   = out
SOURCES   = $(wildcard $(SRC_DIR)/*.java)

.PHONY: all server client stress clean

all: $(OUT_DIR)
	javac -d $(OUT_DIR) $(SOURCES)

$(OUT_DIR):
	mkdir -p $(OUT_DIR)

server: all
	java -cp $(OUT_DIR) Server

client: all
	java -cp $(OUT_DIR) ChatClient

stress: all
	java -cp $(OUT_DIR) StressTest

clean:
	rm -rf $(OUT_DIR)
