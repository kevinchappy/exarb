type=java-large-if-statement-model

dataset_name=java-large-if-statement
data_dir=/home/kev/test/code2seq/data/java-large-if-statement

load_dir=/home/kev/test/code2seq/models/${type}/

test_data=${data_dir}/${dataset_name}.train.c2s



set -e
python3 code2seq.py --load_path ${load_dir} --test ${test_data}
