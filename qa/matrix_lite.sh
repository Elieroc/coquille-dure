#!/bin/bash
# matrix_lite.sh SERIAL PROFIL : base, police 1.3, sombre
cd "$(dirname "$0")"; ser=$1; p=$2
python3 shoot.py $ser ${p}_base; python3 shoot.py $ser ${p}_font13 --font 1.3; python3 shoot.py $ser ${p}_night --night
