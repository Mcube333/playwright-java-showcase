package com.mudassir.framework.data;

/** Dummy card for the demo site's fake payment page. */
public record Card(String nameOnCard, String number, String cvc, String expiryMonth, String expiryYear) {

    public static Card dummy() {
        return new Card("QA Demo", "4111111111111111", "123", "12", "2030"); // pii-allow: public test card
    }
}
