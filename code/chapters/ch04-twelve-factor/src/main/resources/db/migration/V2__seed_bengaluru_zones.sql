-- Reference data for local runs and tests. Production zones arrive through the import-zones admin task.
insert into zone (pincode, area_name, serviceable, delivery_fee_paise) values
    ('560034', 'Koramangala',  true,  2900),
    ('560038', 'Indiranagar',  true,  null),
    ('560102', 'HSR Layout',   true,  3900),
    ('562157', 'Devanahalli',  false, null);
