## Aufgabe 1

-> Youtube und Wikipedia. (weiche 17 wenn Spieler Ass und eine Karte mit Wert 6 hat, da Ass als 1 und 11 gewertet werden kann)

## Aufgabe 2 & 3

Erste Überlegungen für die UDP Implementierung zu Blackjack waren welcher zentral liegt, hier dient der Croupier im Prinzip als Server.
Weitere Ideen waren eigentlich einen 2-Way Handshake in UDP zu nutzen, also das man auf jede Request und ausgehende Nachricht ein ACK erwartet, um somit verlorengegangene Nachrichten erneut zu senden oder die Person aus dem System zu entfernen.
Dann war wie in der letzten Aufgabe die eigentliche Herausforderung die Abstimmung wie wir die Nachrichten verpacken und parsen. Hierzu habe ich mich mit zwei anderen Kommilitonen abgesprochen. Wir nutzen JSON Nachrichten (untersch. Formate dazu stehen im docs: https://docs.google.com/document/d/1niXuoXUmEFF9Z4lBfNpRsipfqm2mRbbPYNEaDwvVmd4/edit?tab=t.ytanwnl134oj). Da wir keinen Kartenzähler hatten, haben wir diesen zwar grob implementiert, aber es so abgeändert, dass der Croupier und Spieler ohne diesen spielen können.


