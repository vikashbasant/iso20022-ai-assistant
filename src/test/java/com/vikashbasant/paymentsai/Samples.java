package com.vikashbasant.paymentsai;

public final class Samples {

    public static final String VALID_MT103 = """
            {1:F01HDFCINBBAXXX0000000000}{2:I103CITIUS33XXXXN}{4:
            :20:INV2026-10-001
            :23B:CRED
            :32A:261030USD1250,50
            :50K:/12345678901
            ACME EXPORTS PVT LTD
            SECTOR 44 GURUGRAM
            HARYANA INDIA
            :52A:HDFCINBBXXX
            :57A:CITIUS33XXX
            :59:/987654321
            GLOBEX CORPORATION
            350 FIFTH AVENUE
            NEW YORK US
            :70:INVOICE 4471 OCT 2026
            :71A:SHA
            -}""";

    public static final String INVALID_MT103 = """
            :20:/BADREF//
            :23B:CRXD
            :32A:261340USD1250.50
            :50K:ACME EXPORTS PVT LTD
            :59:/987654321
            GLOBEX CORPORATION
            :71A:BEN
            :71G:USD10,
            :57A:CITI33
            """;

    private Samples() {
    }
}
