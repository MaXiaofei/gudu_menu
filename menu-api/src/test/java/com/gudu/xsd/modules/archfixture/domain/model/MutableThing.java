package com.gudu.xsd.modules.archfixture.domain.model;

/** ArchUnit 反例夹具：领域实体不继承 AggregateRoot 且带 public setter。 */
public class MutableThing {
    private String name;

    public void setName(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }
}
