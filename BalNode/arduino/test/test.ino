
int ledPin = 13;

void setup() {
    Serial.begin(9600);
    pinMode(ledPin, OUTPUT);
    digitalWrite(ledPin, LOW);
}

void loop() {
    test();
}

void test() {
    char buf[10];
    byte len;
    
    if (Serial.available() > 0) {
        len = Serial.readBytesUntil('\n', buf,10);
        if (len != 0) {
            if ((buf[0] == 't') && (buf[1] == 'i') && (buf[2] == 'c') && (buf[3] == 'k')) {
              digitalWrite(ledPin, HIGH);
              Serial.write("tock");
              delay(500);
              digitalWrite(ledPin, LOW);
            }
        }
    }
}
