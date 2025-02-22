import threading
from itertools import permutations

from confluent_kafka import Consumer, KafkaException, KafkaError
from confluent_kafka.admin import AdminClient
from confluent_kafka.schema_registry import SchemaRegistryClient
from confluent_kafka.schema_registry.avro import AvroDeserializer, AvroSerializer
from confluent_kafka.serialization import StringSerializer, SerializationContext, MessageField
from Module.Messages import UserMessages, um_to_dict, dct_to_um
import asyncio
import sys
import time


class ShowMessages(object):
    def __init__(self, username, topic, bootstrap_servers, sr_config, schema_name):
        self.username = username
        self.topic = topic
        self.bootstrap_servers = bootstrap_servers
        self.sr_config = sr_config
        self.schema_name = schema_name  # "upwork_user_messages"
        self.message_avro_deserializer = None
        self.consumer = None

    def config_message(self):
        # Define Kafka Deserializer and Schema
        schema_registry_client = SchemaRegistryClient(self.sr_config)
        message_schema = schema_registry_client.get_latest_version(self.schema_name)
        self.message_avro_deserializer = AvroDeserializer(schema_registry_client,
                                                          message_schema.schema.schema_str,
                                                          dct_to_um)

        kafka_config = {
            'bootstrap.servers': self.bootstrap_servers,
            'auto.offset.reset': 'earliest',
            'group.id': self.topic + "-consume_by-" + self.username,
        }
        self.consumer = Consumer(kafka_config)
        self.consumer.subscribe([self.topic])

    def display_message(self, name, run_event):
        print("Start", name)
        while run_event.is_set():
            msg = self.consumer.poll(timeout=1.0)
            if msg is not None:
                if msg.topic() == self.topic:
                    auth_msg = self.message_avro_deserializer(msg.value(),
                                                              SerializationContext(msg.topic(), MessageField.VALUE))
                    print("Message:", auth_msg)


if __name__ == "__main__":
    bootstrap_servers = 'localhost:39092,localhost:39093,localhost:39094'
    sr_config = {
        'url': 'http://localhost:8282'
    }
    admin_client = AdminClient({
        "bootstrap.servers": bootstrap_servers
    })

    print('Input people name (split by comma):')
    friends_input = input()
    # friends_input = "jack,mac"
    friends_input = friends_input.replace(",", "-").replace(" ", "_").lower()
    people_list = friends_input.split("-")
    friends_topic = ""
    dict_topics = admin_client.list_topics().topics
    for key, value in dict_topics.items():
        for perm in permutations(people_list):
            perm_topic = '-'.join(map(str, perm))
            perm_topic = 'upwork_user_' + perm_topic
            if perm_topic == key:
                friends_topic = key
                print("Found existing topic:", friends_topic)
                break

    sm = ShowMessages("viewer", friends_topic, bootstrap_servers, sr_config, "upwork_user_messages")
    sm.config_message()

    run_event = threading.Event()
    run_event.set()

    conversation_thread = threading.Thread(target=sm.display_message, args=("Conversation Messages", run_event))

    conversation_thread.start()
    try:
        while 1:
            time.sleep(.1)
    except KeyboardInterrupt:
        print("attempting to close threads")
        run_event.clear()
        conversation_thread.join()
        print("threads successfully closed")
