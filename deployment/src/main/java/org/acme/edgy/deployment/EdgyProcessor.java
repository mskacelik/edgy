package org.acme.edgy.deployment;

import java.util.function.BooleanSupplier;

import org.acme.edgy.runtime.CertificateUpdateEventListener;
import org.acme.edgy.runtime.DynamicRoutingConfigurationProvider;
import org.acme.edgy.runtime.OriginHttpClientManager;
import org.acme.edgy.runtime.RouterConfigurator;
import org.acme.edgy.runtime.config.EdgyConfig;
import org.acme.edgy.runtime.tracing.TracingProxyObserver;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;

class EdgyProcessor {

    private static final String FEATURE = "edgy";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void setupAdditionalBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(new AdditionalBeanBuildItem(CertificateUpdateEventListener.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(OriginHttpClientManager.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(RouterConfigurator.class));
    }

    @BuildStep(onlyIf = IsDynamicallyConfigured.class)
    AdditionalBeanBuildItem addDynamicRoutingProvider() {
        return new AdditionalBeanBuildItem(DynamicRoutingConfigurationProvider.class);
    }

    static class IsDynamicallyConfigured implements BooleanSupplier {

        EdgyConfig config;

        @Override
        public boolean getAsBoolean() {
            return config.mode() == EdgyConfig.Mode.CONFIGURATION;
        }
    }

    @BuildStep(onlyIf = IsTracingEnabled.class)
    void setupTracingObserver(Capabilities capabilities,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        if (!capabilities.isPresent(Capability.OPENTELEMETRY_TRACER)) {
            throw new ConfigurationException(
                    "edgy.tracing.enabled=true requires the quarkus-opentelemetry extension. "
                            + "Please add 'io.quarkus:quarkus-opentelemetry' to your dependencies.");
        }
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(TracingProxyObserver.class));
    }

    static class IsTracingEnabled implements BooleanSupplier {

        EdgyConfig config;

        @Override
        public boolean getAsBoolean() {
            return config.tracing().enabled();
        }
    }
}
