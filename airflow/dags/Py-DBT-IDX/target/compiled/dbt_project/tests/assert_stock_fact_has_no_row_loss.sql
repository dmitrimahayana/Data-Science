-- fact_stocks must have the same number of rows as its staging counterpart
-- Therefore return records where this isn't true to make the test fail
select *
from (
    select stock.id
    from "my_database"."public"."fact_stocks" stock
    where stock.id is null
) tmp