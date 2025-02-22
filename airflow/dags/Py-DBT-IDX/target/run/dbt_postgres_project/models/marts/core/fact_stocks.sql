
  
    

  create  table "my_database"."public"."fact_stocks__dbt_tmp"
  as (
    -- noinspection SqlDialectInspectionForFile

with stock as (
    select *
    from "my_database"."public"."stg_eltool__stock"
    ),
     company as (
         select *
         from "my_database"."public"."stg_eltool__company"
     )
select a.id,
       a.ticker,
       b.name,
       a.date,
       a.open,
       a.high,
       a.low,
       a.close,
       a.volume,
       b.logo
from stock a
    join company b on a.ticker = b.ticker
  );
  