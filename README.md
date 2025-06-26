# Hausaufgabe 5

## Usage

Es ist wichtig, dass das Projekt mit Gradle gebaut wird, da ich Logging verwendet habe und der Ordner für die log-Dateien im Build Prozess erstellt wird.
Sollte es zu Runtime-Exceptions kommen, liegt dies höchst wahrscheinlich am fehlenden `logs` Ordner im Root Verzeichnis des Projektes.
Es kann versucht werden den Ordner manuell zu erstellen.
Hilft dies nicht kann, der Aufruf der `log` Funktion in der Klasse `UDP_Endpoint` auskommentiert werden.

### Dealer

```bash
./gradlew run --console=plain --args="--role=dealer"
```

Der Dealer kann das Spiel starten und pausieren mit den Befehlen

```bash
start
```

```bash
pause
```

### Player

```bash
./gradlew run --console=plain --args="--role=player --strategy=flat"
```

Bevor mit der Simulation von Blackjack begonnen werden kann, muss sich der Spieler bei Dealer und Counter registrieren.
Dies kann er mit dem Befehl

```bash
register <ip> <port>
```

### Counter

```bash
./gradlew run --console=plain --args="--role=counter"
```

Der Counter muss sich beim Dealer registrieren ebenfalls mit dem Befehl

```bash
register <ip> <port>
```

Der Counter kann die Statistik der bisherigen Spiele in eine CSV Datei exportieren mit dem Befehl

```bash
save
```

## Aufgabe 2

### Quittierungsbetrieb

Allgemein können wir nicht sicherstellen, dass die Nachrichten wie gewünscht ankommen.
Wir können aber sicherstellen, dass wir Fehler in der Kommunikation bemerken.
Hierfür implementieren wir einen Quittierungsbetrieb, der den Erhalt der Nachrichten bestätigt.
Sobald eine Nachricht erhalten wird, schicken wir eine Empfangsbestätigung an den Versender der Nachricht.
Der Versender versucht, solange seine Nachricht zu verschicken, wie er keine Empfangsbestätigung erhält.
Dabei wartet er für jede Nachricht eine bestimmte Zeit und bricht ab, sobald die maximale Anzahl der Versuche überschritten ist.
Hier wirft das Programm eine Exception, die entsprechend dem aktuellen Zustand behandelt werden kann.

Um zu verhindern, dass Nachrichten mehrfach verarbeitet werden wird jede Nachricht nummeriert.
Wir kombinieren diese Nummerierung mit einer eindeutigen Identifizierung von Instanzen der Programme.
Hierfür kombinieren wir die 6 Byte große MAC-Adresse mit der 2 Byte großen Portnummer.
Diese Identifizierung schicken wir mit jeder Nachricht in einem Header mit.
Die Kombination aus Instanzen ID und Nachrichten Nummer identifiziert eindeutig jede Nachricht, die über das Netzwerk versendet wird.
Diese Kombination nennen wir Nachrichten ID.
Wir speichern die Nachrichten IDs der letzten $n$ erhaltenen Nachrichten.
Bei Erhalt einer Nachricht prüfen wir, ob wir die Nachricht bereits erhalten haben.
Die Nachricht wird nur weiterverarbeitet, wenn sie nicht bereits verarbeitet wurde.

### Header

Der Header einer Nachricht kann wie folgt implementiert werden

<table>
<tr>
    <th>bytes</th>
    <th style="text-align:center;">1</th>
    <th style="text-align:center;">2</th>
    <th style="text-align:center;">3</th>
    <th style="text-align:center;">4</th>
    <th style="text-align:center;">5</th>
    <th style="text-align:center;">6</th>
    <th style="text-align:center;">7</th>
    <th style="text-align:center;">8</th>
</tr>
<tr>
    <th>1-8</th>
    <td colspan='4' style="text-align:center;">length</td>
    <td colspan='4' style="text-align:center;">number</td>
</tr>
    <th>9-16</th>
    <td colspan='8' style="text-align:center;">instance id</td>
</tr>
<tr>
    <th>17-24</th>
    <td style="text-align:center;">t </td>
    <td style="text-align:center;">r</td>
    <td style="text-align:center;">0</td>
    <td style="text-align:center;">0</td>
    <td style="text-align:center;">0</td>
    <td style="text-align:center;">0</td>
    <td style="text-align:center;">0</td>
    <td style="text-align:center;">0</td>
</tr>
<tr>
    <th>25-4096</th>
    <td colspan='8' style="text-align:center;">Payload</td>
</tr>
</table>

- t = message type, t $\in \{1,2,3,4,5,6,7,8,9,10,11,12\}$
- r = sender role, r $\in \{1,2,3\}$

| message type | Funktion       |
|--------------|----------------|
| 1            | ACK            |
| 2            | SYN            |
| 3            | FIN            |
| 4            | BET            |
| 5            | ACTION_REQUEST |
| 6            | ACTION         |
| 7            | WINNIGS        |
| 8            | DECKCOUNT      |
| 9            | SHUFFLED       |
| 10           | CARDS          |
| 11           | UPCARD         |
| 12           | STATISTICS     |

| sender role | Funktion |
|-------------|----------|
| 1           | Dealer   |
| 2           | Player   |
| 3           | Counter  |

### Payload

Der Payload kann verschiedene Formate je nach Nachrichtentyp annehmen.
Karten werden in einem Byte kodiert.
Hierbei werden die Zahlen von 1 bis 52 verwendet.

| Bytes | ♧ | ♢ | ♡ | ♤ |
|-------|-----|----|----|----|
| 1-4   | A     | A        | A      | A      |
| 5-8   | 2     | 2        | 2      | 2      |
| 9-12  | 3     | 3        | 3      | 3      |
| 13-16 | 4     | 5        | 4      | 4      |
| 17-20 | 5     | 5        | 5      | 5      |
| 21-24 | 6     | 6        | 6      | 6      |
| 25-28 | 7     | 7        | 7      | 7      |
| 29-32 | 8     | 8        | 8      | 8      |
| 33-36 | 9     | 9        | 9      | 9      |
| 37-40 | 10    | 10       | 10     | 10     |
| 41-44 | J     | J        | J      | J      |
| 45-48 | Q     | Q        | Q      | Q      |
| 49-52 | k     | K        | K      | K      |

Ganzzahlen werden in 4 Bytes kodiert. Spielaktionen werden in einem Byte kodiert, genauso wie Booleans.

| Byte | Aktion      |
|------|-------------|
| 1    | Hit         |
| 2    | Stand       |
| 3    | Double Down |
| 4    | Split       |
| 5    | Surrender   |

Es ist klar, dass man mit dieser Konvention alle Informationen kodieren kann, die ausgetauscht werden müssen.
Ein Eintrag für die Statistik ist eine Konkatenation von mit diesen Konventionen kodierten Datentypen.

### Aufgabe 4

Ich habe für die letzte Aufgabe frühzeitig versucht mich mit meinen Kommilitonen abzusprechen, indem ich meinen Arbeitsprozess dokumentiere.
Aber da bis auf einen Kommilitonen, und dieser für mich zu spät, sich niemand daran beteiligt hat, habe ich entschieden bei dieser Aufgabe alleine zu arbeiten. Dementsprechend hatte ich keine Probleme mit der Abstimmung mit anderen Kommilitonen.
Aber ich musste mehrfach Änderungen am Nachrichten Protokoll vornehmen, da im Laufe der Entwicklung neue Erkenntnisse einfließen mussten.
Die größte Herausforderung war die korrekte Implementierung des `GameThread`, da ich etwas aus der Übung war, was das Thema Multithreading angeht.
Die Logik für die Berechnung des Erwartungswerts wurde aus Zeitgründen KI generiert. Sie sieht für mich aber korrekt aus und gelangt auch zu nachvollziehbaren Entscheidungen, die im Einklang mit Basic-Strategy stehen.
