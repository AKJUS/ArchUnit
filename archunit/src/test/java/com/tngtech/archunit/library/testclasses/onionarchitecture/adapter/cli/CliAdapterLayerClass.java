package com.tngtech.archunit.library.testclasses.onionarchitecture.adapter.cli;

import com.tngtech.archunit.library.testclasses.onionarchitecture.adapter.persistence.PersistenceAdapterLayerClass;
import com.tngtech.archunit.library.testclasses.onionarchitecture.adapter.rest.RestAdapterLayerClass;
import com.tngtech.archunit.library.testclasses.onionarchitecture.application.ApplicationLayerClass;
import com.tngtech.archunit.library.testclasses.onionarchitecture.domain.model.DomainModelLayerClass;
import com.tngtech.archunit.library.testclasses.onionarchitecture.domain.service.DomainServiceLayerClass;

public class CliAdapterLayerClass {
    private DomainModelLayerClass domainModelLayerClass;
    private DomainServiceLayerClass domainServiceLayerClass;
    private ApplicationLayerClass applicationLayerClass;
    private CliAdapterLayerClass cliAdapterLayerClass;
    private PersistenceAdapterLayerClass persistenceAdapterLayerClass;
    private RestAdapterLayerClass restAdapterLayerClass;

    private void call() {
        domainModelLayerClass.callMe();
        domainServiceLayerClass.callMe();
        applicationLayerClass.callMe();
        cliAdapterLayerClass.callMe();
        persistenceAdapterLayerClass.callMe();
        restAdapterLayerClass.callMe();
    }

    public void callMe() {
    }
}
