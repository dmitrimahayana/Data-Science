select
      count(*) as failures,
      count(*) != 0 as should_warn,
      count(*) != 0 as should_error
    from (
      
    
    



select id
from "my_database"."public"."stg_eltool__stock"
where id is null



      
    ) dbt_internal_test