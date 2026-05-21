open module demo.module {
    requires java.base;
    requires static java.sql;
    requires transitive java.logging;

    exports demo.module.api;
    exports demo.module.spi to consumer.alpha, consumer.beta;
    opens demo.module.internal to framework.one, framework.two;
    uses demo.module.spi.Plugin;
    provides demo.module.spi.Plugin with demo.module.impl.DefaultPlugin, demo.module.impl.FastPlugin;
}
