# Uebungsgblatt 06

## Aufgabe 1: Konzepte

* _*Sliding Window*_: Bei dem Sliding Window Verfahren wird dem Sender eine Kontrolle über die Datenpakete bzw. den Datenfluss ermöglicht, ohne dass er speziell auf ACKs wartet. Der Sender führt eine Liste, die entsprechend der Anzahl der Frames, die versendet werden dürfen, entspricht. So können vorerst ohne ACKs weitere Pakete versendet werden ohne das der Datenfluss unterbrochen wird. Wenn nun beim Empfänger dieses Paket ankommt, sendet dieser dennoch ein ACK zurück, welches beim Sender dann das nächste Frame übertragen lässt (sprich man verschiebt ein Fenster weiter -> Window Sliding). Wenn im Falle allerdings keine ACK Bestätigung innerhalb eines bestimmten Timeout Fensters zurück kommt, dann wird in der Regel versucht das Frame erneut zu übertragen.

* _*TCP Tahoe*_: TCP Tahoe ist eins der ersten TCP Protokolle mit einer Überlastungskontrolle. Es arbeitet wie TCP Reno bei 3 doppelten ACK Bestätigungen. Die erste Phase ist der Slow Start, welcher zu Beginn einer Verbindung (oder nach einem Timeout) die Übertragungsrate exponentiell erhöht, bis ein bestimmtes Überlstungsfenster erreicht ist. Dies allerdings nur bis zu einem gewissen Schwellenwert. Sollte dieser überschritten werden, dann wird die Übertragungsrate nur noch linear erhöht (cwnd um 1). Wenn nun drei doppelte ACK Bestätigungen für das gleiche Überlastungsfenster ankommen, dann wird das Paket erneut gesendet ohne auf etwas zu warten. Wenn jedoch die Timeouts vorher auftreten, wird das Überlastungsfenster auf 1 zurückgesetzt sowie es wieder im Slow Start Modus anfängt.

* _*TCP Reno*_: TCP Reno erweitert TCP Tahoe um den Fast Recovery, um es hierbei effizienter auf Paketverluste reagieren zu lassen. Letztlich ist es nur eine Ergänzung zu TCP Tahoe, sodass eigentlich TCP Reno = TCP Tahoe + Fast Recovery gilt. Also wird hier nach dem Fast Retransmit, also der letzten Phase des TCP Tahoe, nun nicht in den Slow Start sondern in den Fast Recovery gesetzt. Dabei wird cwnd also die Überlastungsfenster auf die Hälfte reduziert. Da zwar doppelte ACKs ankommen, wird so einer funktionierenden Verbindung weiterhin der Datenaustausch ermöglicht. Wenn dann aber der Verlust eines Paketes entdeckt wird, dann wird alles zurückgesetzt.

* _*TCP Vegas*_: TCP Vegas ist im Gegensatz zu TCP Tahoe und TCP Reno nicht auf den Paketverlust fokussiert sondern mehr auf die Vermeidung von Datenstau. Hierzu wird primär die Round-Trip-Time (RTT; Dauer vom Senden des Pakets bis zur ACK-Bestätigung) der Pakete gemessen und ausgewertet. Einmal wird die tatsächliche Durchsatzrate anhand der RTT der gesendeten und empfangenen Pakete ermittelt. Die erwartete Durchsatzrate wird an einer minimalen RTT, einer BaseRTT, und der Überlastungsfenster berechnet. Wenn sich nun die tatsächliche Durchsatzrate um einen bestimmten Schwellenwert (alpha) die erwartete Durchsatzrate überschreitet, dann wird das Überlastungsfenster verringert, wenn jedoch die tatsächliche Durchsatzrate unterhalb eines Schwellenwerts (beta) liegt, dann wird das Überlastungsfenster erhöht, um so die Netzwerkauslastung zu erhöhen und optimalen Datenfluss zu ermöglichen.


### TCP Tahoe: Verlauf von cwnd Überlastungsfenster (Congestion Window)
```
Zeit (Round-Trip Times) →
┌────────────────────────────────────────────────────────────────────┐
│     Slow Start          →       Congestion Avoidance     →         │
│ cwnd wächst exponentiell         cwnd wächst linear                │
│                                                                    │
│ cwnd                                                               │
│  ^                                                                 │
│  |                                                                 │
│  |                                                                 │
│  |                                                            *    | ← Paketverlust(Timeout)
│  |                                                         *       │
│  |                                                    *            │
│  |                                              *                  │
│  |                                        *                        │
│  |                                  *                              │
│  |                            *                                    │
│  |                      *                                          │
│  |                *                                                │
│  |           *                                                     │
│  |       *                                                         │
│  |    *                                                            │
│  |  *                                                              │
│  | *                                                               │
│  +----------------------------------------------------------------> Zeit
│     cwnd = 1       ssthresh erreicht → linearer Anstieg → Timeout
│                   (z. B. ssthresh = 16)                  cwnd = 1  ← Restart Slow Start
└────────────────────────────────────────────────────────────────────┘
```

| Protokoll | ISO/OSI - Layer | Begründung |
| --------- | --------------- | ---------- |
| IP | 3.Schicht - Network Layer | Adressierung und Routing von Datenpaketen -> Wegweiser vom Sender zum Empfänger |
| ICMP | 3.Schicht - Network Layer | spezielle Form der IP, -> Fehlermeldungen und Statusinformationen |
| ARP | 3.Schicht - Network Layer | IP -> MAC für lokale Zustellungen |
| RARP | 3.Schicht - Network Layer | Reverse ARP also MAC -> IP |
| UDP | 4.Schicht - Transport Layer | Ende zu Ende Übertragung, schnell, kostengünstig und ohne Fehlerüberprüfung |
| TCP | 4.Schicht - Transport Layer | Wie UDP ENde zu Ende nur mit Kontrolle über Verbindungsaufbau und über Verbindung hinweg (3-Way Handshake) |
| SFTP | 7.Schicht - Application Layer | Läuft über SSH, bietet Dateiübertragung mit Authentifizierung & Verschlüsselung |
| SSH | 7.Schicht - Application Layer | sichere Terminalverbindungen (Tunnelverbindungen) |
| SMTP | 7.Schicht - Application Layer | primär für Versand von E-Mails genutzt |
| DNS | 7.Schicht - Application Layer | übersetzt Domainnamen in IP-Adressen |
| NTP | 7.Schicht - Application Layer | Zeitsynchronisatio über Netzwerke |
| HTTP | 7.Schicht - Application Layer | Web-Kommunikation -> Grundlage |
| HTTPS | 7.Schicht - Application Layer | HTTP über TSL/SSL |

## Aufgabe 2: Nmap

* _Aufgabe 2a_:  Wie viele Hosts befinden sich in ihrem lokalen Klasse-C-Netz?

                Befehl nmap -sn <lokaleIP>/24, da Klasse C-Netz über Subnetzmaske meist 254 mögliche Hosts ermöglicht. -sn zeigt welche Hosts erreichbar sind.

                -> Antwort: 17 hosts up

* _Aufgabe 2b_: Welches Betriebssystem wird von scanme.nmap.org verwendet?

                Befehl: nmap -O scanme.nmap.org; -O Aktiviert OS-Erkennung

                -> OS details: Linux 4.15 - 5.19

* _Aufgabe 2c_: An welchem Datum wurde die Webseite nmap.org registriert?


                Befehl: <pfad zu whois.exe> nmap.org

                -> Creation Date: 1999-01-18T05:00:00Z

* _Aufgabe 2d_: Wie kann man möglichst effektiv eine größere Menge an Adressen nach offenen TCP-Ports scannen?

                Befehl: nmap -T4 -p 1-1000 <IP>, -T4 aggressiver Scan, -p 1-1000 beschränkung für zu scannende Ports (meist nur wichtigste)


* _Aufgabe 2e_: Wie funktioniert der SYN-Scan und für was kann man ihn verwenden?

                Befehl: nmap -sS <IP>

                TCP-SYN wird an Port gesendet -> Wenn Port offen, dann SYN/ACK kommt zurück, nmap verwirft ACKs

* _Aufgabe 2f_: Welches sind die offenen Ports, die bei Ihren bisherigen Nmap-Scans am häufigsten auftreten, und wofür werden sie verwendet?

                ->  Port 80 - HTTP
                    Port 139 - NetBIOS zur Druckfreigabe oder Dateifreigabe
                    Port 443 - HTTPS
                    Port 445 - microsoft-ds, glaube Windows Filesharing oder so
                    Port 3389 - RDP, Remote Desktop


## Aufgabe 3: DHCP

- Capture Filter: "udp port 67 or udp port 68"

In Wireshark hab ich, wie in [DHCP_Packages](A_03/DHCP_packages.pcapng) zu sehen, 4 DHCP Pakete über "ipconfig /release" und "ipconfig /renew" abgefangen. Diese 4 sind das Discover, Offer, Reqeuest und ACK Package. Außerdem gibt es sonst noch das Release Package.

- [Paket 1 - Discover](A_03/DHCP_Discover.PNG):
    * Message Type: DHCP Discover - Bedeutet der Client such einen DHCP-Server
    * Client Identifier -> Client MAC-Adress
    * Requested IP: 192.168.178.40 
    * HOST-Name
    
    - Also zu diesem Zeitpunkt ist noch keine gültige IP Adresse vergeben, sondern wird mit dem Discover Paket mit gewünschter IP erst angefragt


- [Paket 2 - Offer](A_03/DHCP_Offer.PNG):
    * Message Type: DHCP Offer - Server bietet also Adresse an (optimal auch gewünschhte IP)
    * Your IP Address: 192.168.178.40 - vorgeschlagene IP
    * DHCP Server Identifier: 192.168.178.1 - IP-Adresse des DHCP-Servers
    * Lease Time
    * Subnet Mask, Router, DNS

    - Ausgabe der vorgeschlagenen IP Adresse sowie weitergabe von nötigen Information wie Subnetzmaske usw.


- [Paket 3 - Request](A_03/DHCP_Request.PNG):
    * Message Type: DHCP Request 
    * Requested IP Address: 192.168.178.40
    * DHCP Server Identifier: 192.168.178.1 
    * Client Identifier: Client MAC-Adress
    * Parameter Request List: ... - Client fordert spezifische Informationen an(SubnetMask, DNS, ...)
    * Client MAC

    - Client bestätigt den Vorschlag des Servers und nimmt IP-Adress wie oben gezeigt, 192.168.178.40 an


- [Paket 4 - ACK](A_03/DHCP_ACK.PNG):
    * Message Type: DHCP ACK
    * Your IP Address: 192.168.178.49
    * DHCP Server Identifier: 192.168.178.1
    * Lease Time
    * Subnet Mask, Router, DNS - also erhalt von angeforderten Informationen
    * Client MAC

    - Server bestätigt mit ACK-Paket die Anfrage des Clients und weist ihm mitgeteilte IP-Adresse (192.168.178.40) zu und gibt weitere angeforderte Informationen weiter (Subnetzmaske usw.)


## Aufgabe 4: Routing
siehe [Aufgabe_Routing](A_04_Routing.pdf)