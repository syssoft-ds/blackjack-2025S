Start von Spieler.py:

python Spieler.py --listen_ip <IP-Adresse> --listen_port 12346 --croupier_ip <IP-Adresse_Croupier> --croupier_port 12345 --card_counter_ip <IP-Adresse_CardCounter>
--card_counter_port 12347


Mehrere Spieler: Wenn du mehrere Spieler auf demselben Rechner starten möchtest, musst du jedem Spieler einen unterschiedlichen --listen_port geben. Zum Beispiel:

python Spieler.py --listen_ip <IP-Adresse> --listen_port 12346 ...
python Spieler.py --listen_ip <IP-Adresse> --listen_port 12348 ... (und so weiter)
