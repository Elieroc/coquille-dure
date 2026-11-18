#!/bin/bash
# matrix.sh SERIAL PROFIL DENSITE_DEFAUT : joue les 5 variantes sur un appareil
ser=$1; p=$2; d=$3; cd "$(dirname "$0")"
python3 shoot.py $ser ${p}_base
python3 shoot.py $ser ${p}_font13 --font 1.3
python3 shoot.py $ser ${p}_font20 --font 2.0
python3 shoot.py $ser ${p}_night --night
python3 shoot.py $ser ${p}_dens --dens $((d*13/10))
