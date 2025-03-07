USE [test_normalization];
    execute('create view _airbyte_test_normalization."conflict_stream_name_ab1__dbt_tmp" as
    
-- SQL model to parse JSON blob stored in a single column and extract into separated field columns as described by the JSON Schema
select
    json_value(_airbyte_data, ''$."id"'') as id,
    json_query(_airbyte_data, ''$."conflict_stream_name"'') as conflict_stream_name,
    _airbyte_emitted_at
from "test_normalization".test_normalization._airbyte_raw_conflict_stream_name as table_alias
-- conflict_stream_name
    ');

