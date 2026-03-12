#!/bin/bash
if [ -z "$KIZZ_DIR" ]; then echo "ERROR: KIZZ_DIR is not defined"; exit 1; fi
rsync -a --delete "GodotPlayGameServices/" "$KIZZ_DIR/addons/GodotPlayGameServices/"