# Discovery-Pools nachfüllen

- Nach erfolgreicher Entdeckung füllen alle Tore ihre Standortpools aus bereits geprüften freien Sektoren im eigenen Suchradius wieder auf. Ein leerer Quellpool kann so geeignete Standorte anderer Tore nutzen.
- Belegte Sektoren werden nicht erneut durchsucht. Sektoren ohne geeigneten Chunk werden nach einer Wartezeit erneut geprüft; ungültig gewordene Chunks werden aus allen Pools entfernt.
- Die konfigurierte Erfolgswahrscheinlichkeit, Abklingzeit, Geländeprüfung, Gate-IDs und bestehenden Daten bleiben erhalten. Kein Relay-, Protokoll- oder Datenbankschemawechsel.
