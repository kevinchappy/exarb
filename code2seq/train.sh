###########################################################
# Change the following values to train a new model.
# type: the name of the new model, only affects the saved file name.
# dataset: the name of the dataset, as was preprocessed using preprocess.sh
# test_data: by default, points to the validation set, since this is the set that
#   will be evaluated after each training iteration. If you wish to test
#   on the final (held-out) test set, change 'val' to 'test'.

type=java-large-if-statement-model
dataset_name=java-large-if-statement
data_dir=/home/kev/test/code2seq/data/java-large-if-statement

data=${data_dir}/${dataset_name}
test_data=${data_dir}/${dataset_name}.val.c2s

model_dir=/home/kev/test/code2seq/models/${type}/
checkpoint_dir=/home/kev/test/code2seq/modelcheckpoints/${type}/

mkdir -p ${model_dir}
mkdir -p ${checkpoint_dir}
set -e
python3 -u code2seq.py --data ${data} --test ${test_data} --model_path ${checkpoint_dir} --save_path ${model_dir}
