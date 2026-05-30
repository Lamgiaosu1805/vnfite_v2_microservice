package com.p2plending.matching.domain.enums;

public enum MatchStatus {
    PENDING,    // match found, event not yet published
    NOTIFIED,   // match.found published to Kafka
    ACCEPTED,   // investor acted on the match
    REJECTED,   // investor declined
    EXPIRED     // loan was funded before investor acted
}
