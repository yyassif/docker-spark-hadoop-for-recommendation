#!/usr/bin/env python3

import os
import random
import sys

import pandas as pd


def find_key(dictionary, target_value):
    for key, value in dictionary.items():
        if value == target_value:
            return key

def encode_and_save_dataset(file_path, save_path):
    # Read CSV file using pandas
    df = pd.read_csv(file_path)

    # Create mappings for user and product IDs to integers
    user_id_mapping = {}
    product_id_mapping = {}
    user_counter = 1
    product_counter = 1

    ratings_output = []
    products_output = []

    # Process each row in the dataset
    for index, row in df.iterrows():
        # Encode user ID
        user_id = row['userID']
        if user_id not in user_id_mapping:
            user_id_mapping[user_id] = user_counter
            user_counter += 1

        # Encode product ID
        product_id = row['productID']
        if product_id not in product_id_mapping:
            product_id_mapping[product_id] = product_counter
            products_output.append((product_counter, product_id))
            product_counter += 1

        # Append the encoded record to ratings_output
        ratings_output.append((user_id_mapping[user_id], product_id_mapping[product_id], row['rating'], row['timestamp']))

    # Write ratings.txt
    with open(os.path.join(save_path, 'ratings.txt'), 'w') as ratings_file:
        for record in ratings_output:
            ratings_file.write(f"{record[0]}::{record[1]}::{record[2]}::{record[3]}\n")

    # Write products.txt
    with open(os.path.join(save_path, 'products.txt'), 'w') as products_file:
        for record in products_output:
            products_file.write(f"{record[0]}::{record[1]}\n")
    
    # Write personalRatings.txt
    with open(os.path.join(save_path, 'personalRatings.txt'), 'w') as personal_ratings_file:
        for record in random.sample(ratings_output, 50):
            rating = random.randint(0, 5)
            product_id = find_key(product_id_mapping, record[1])
            personal_ratings_file.write(f"0::{record[1]}::{rating}::{record[3]}::{product_id}\n")


def main(root_directory):
    dataset_path = os.path.join(root_directory, 'dataset')
    raw_dataset_path = os.path.join(root_directory, 'raw-dataset')

    # Create the 'dataset' directory if it doesn't exist
    os.makedirs(dataset_path, exist_ok=True)

    for class_dir in os.listdir(raw_dataset_path):
        class_path = os.path.join(raw_dataset_path, class_dir)
        if os.path.isdir(class_path) and not class_dir.startswith("."):
            file_path = f"{class_path}/data.csv"

            # Check if the file exists
            if os.path.exists(file_path):
                # Create the 'class' directory within 'dataset'
                class_dataset_path = os.path.join(dataset_path, class_dir)
                os.makedirs(class_dataset_path, exist_ok=True)

                # Encode & Save the Dataset
                encode_and_save_dataset(file_path, class_dataset_path)

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python encode-generate-dataset.py root_directory")
    else:
        root_directory = sys.argv[1]
        main(root_directory)
