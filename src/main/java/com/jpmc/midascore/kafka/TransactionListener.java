package com.jpmc.midascore.kafka;

import java.util.Optional;
import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.repository.TransactionRecordRepository;
import com.jpmc.midascore.repository.UserRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.jpmc.midascore.foundation.Incentive;
import org.springframework.web.client.RestTemplate;


@Component
public class TransactionListener {

    private final UserRepository userRepository;
    private final TransactionRecordRepository transactionRecordRepository;
    private final RestTemplate restTemplate;


    public TransactionListener(UserRepository userRepository,
                               TransactionRecordRepository transactionRecordRepository,
                               RestTemplate restTemplate) {
        this.userRepository = userRepository;
        this.transactionRecordRepository = transactionRecordRepository;
        this.restTemplate = restTemplate;
    }


    @KafkaListener(
            topics = "${general.kafka-topic}",
            groupId = "midas-core-group"
    )

    @KafkaListener(
            topics = "${general.kafka-topic}",
            groupId = "midas-core-group"
    )
    public void listen(Transaction transaction) {

        Optional<UserRecord> senderOpt =
                userRepository.findById(transaction.getSenderId());

        Optional<UserRecord> recipientOpt =
                userRepository.findById(transaction.getRecipientId());

        Incentive incentive = restTemplate.postForObject(
                "http://localhost:8080/incentive",
                transaction,
                Incentive.class
        );

        float incentiveAmount =
                incentive != null ? incentive.getAmount() : 0f;


        // validate sender & recipient
        if (senderOpt.isEmpty() || recipientOpt.isEmpty()) {
            return;
        }

        UserRecord sender = senderOpt.get();
        UserRecord recipient = recipientOpt.get();

        // validate balance
        if (sender.getBalance() < transaction.getAmount()) {
            return;
        }

        // update balances
        sender.setBalance(sender.getBalance() - transaction.getAmount());
        recipient.setBalance(
                recipient.getBalance() + transaction.getAmount() + incentiveAmount
        );


        userRepository.save(sender);
        userRepository.save(recipient);

        // record transaction
        TransactionRecord record =
                new TransactionRecord(
                        sender,
                        recipient,
                        transaction.getAmount(),
                        incentiveAmount
                );

        transactionRecordRepository.save(record);

    }

}
