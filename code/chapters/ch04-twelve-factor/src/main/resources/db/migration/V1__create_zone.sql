-- Flyway migration. Schema changes are versioned with the code and applied by the release, not by hand.
create table zone (
    pincode            char(6)      primary key,
    area_name          varchar(120) not null,
    serviceable        boolean      not null,
    delivery_fee_paise bigint       check (delivery_fee_paise >= 0)
);
