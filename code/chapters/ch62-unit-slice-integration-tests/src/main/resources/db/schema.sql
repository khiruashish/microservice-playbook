create table coupon_redemptions (
    customer_id varchar(64) not null,
    coupon      varchar(32) not null,
    order_id    bigint      not null,
    redeemed_at timestamp with time zone not null default current_timestamp,
    primary key (customer_id, coupon)
);
