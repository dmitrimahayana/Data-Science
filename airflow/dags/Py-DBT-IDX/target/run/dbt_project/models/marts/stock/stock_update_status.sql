
  create view "my_database"."public"."stock_update_status__dbt_tmp"
    
    
  as (
    with stock as (select *,
                      ROW_NUMBER() OVER (PARTITION BY ticker ORDER BY date desc) AS rownum
               from "my_database"."public"."fact_stocks")
select * from (
    select a.id,
          a.ticker,
          a.name,
          a.date,
          a.open,
          a.high,
          a.low,
          a.close,
          a.volume,
          a.rownum,
          DENSE_RANK() OVER (PARTITION BY a.ticker ORDER BY a.rownum desc) AS ranking
   from stock a
   ) b
   order by b.ranking asc, b.ticker asc
  );