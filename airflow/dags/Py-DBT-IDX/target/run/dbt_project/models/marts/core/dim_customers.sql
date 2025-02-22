
  create view "my_database"."public"."dim_customers__dbt_tmp"
    
    
  as (
    -- noinspection SqlDialectInspectionForFile

with customers as (
    select *
    from "my_database"."public"."stg_eltool__customers_snapshots"
    ),
     state as (
         select *
         from "my_database"."public"."stg_eltool__state"
     )
select c.customer_id,
       c.zipcode,
       c.city,
       c.state_code,
       s.state_name,
       c.datetime_created,
       c.datetime_updated,
       c.dbt_valid_from::TIMESTAMP as valid_from,
        CASE WHEN c.dbt_valid_to IS NULL
            THEN '9999-12-31'::TIMESTAMP
            ELSE c.dbt_valid_to::TIMESTAMP
        END as valid_to
from customers c
    join state s on c.state_code = s.state_code
  );