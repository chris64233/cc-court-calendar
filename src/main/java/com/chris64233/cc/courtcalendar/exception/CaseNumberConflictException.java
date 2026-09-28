package com.chris64233.cc.courtcalendar.exception;

/**
 * 案件号已存在。
 */
public class CaseNumberConflictException extends RuntimeException {

    private final String caseNumber;

    public CaseNumberConflictException(String caseNumber) {
        super("案件号已存在: " + caseNumber);
        this.caseNumber = caseNumber;
    }

    public String getCaseNumber() {
        return caseNumber;
    }
}
