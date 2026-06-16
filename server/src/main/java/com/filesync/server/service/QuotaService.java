package com.filesync.server.service;

import com.filesync.server.domain.User;
import com.filesync.server.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuotaService {
    private final UserRepository userRepository;

    public QuotaService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ========== Main Quota Operations ==========

    @Transactional
    public void checkAndReserveQuota(String username, long fileSize) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        if (user.getIsAdmin()) return;

        if (user.getIsDemo()) {
            long newTotal = user.getTotalStorageBytes() + fileSize;
            if (newTotal > user.getMaxStorageBytes()) {
                throw new QuotaExceededException("Storage quota exceeded. Maximum " +
                        user.getMaxStorageBytes() / (1024 * 1024) + " MB.");
            }
            int newFileCount = user.getFileCount() + 1;
            if (newFileCount > user.getMaxFileCount()) {
                throw new QuotaExceededException("File count quota exceeded. Maximum " +
                        user.getMaxFileCount() + " files.");
            }
        }

        user.setTotalStorageBytes(user.getTotalStorageBytes() + fileSize);
        user.setFileCount(user.getFileCount() + 1);
        userRepository.save(user);
    }

    @Transactional
    public void releaseQuota(String username, long fileSize) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        long newTotal = user.getTotalStorageBytes() - fileSize;
        if (newTotal < 0) newTotal = 0;
        user.setTotalStorageBytes(newTotal);

        int newCount = user.getFileCount() - 1;
        if (newCount < 0) newCount = 0;
        user.setFileCount(newCount);

        userRepository.save(user);
    }

    // ========== Legacy Methods ==========

    @Transactional
    public void updateUserQuota(String username, long addedSize) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        user.setTotalStorageBytes(user.getTotalStorageBytes() + addedSize);
        userRepository.save(user);
    }

    @Transactional
    public void decrementUserQuota(String username, long removedSize) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        long newTotal = user.getTotalStorageBytes() - removedSize;
        if (newTotal < 0) newTotal = 0;
        user.setTotalStorageBytes(newTotal);
        userRepository.save(user);
    }

    @Transactional
    public void incrementFileCount(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        user.setFileCount(user.getFileCount() + 1);
        userRepository.save(user);
    }

    @Transactional
    public void decrementFileCount(String username, int count) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        int newCount = user.getFileCount() - count;
        if (newCount < 0) newCount = 0;
        user.setFileCount(newCount);
        userRepository.save(user);
    }

    @Deprecated
    public void checkUploadQuota(String username, long fileSize, boolean isNewFile) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        if (user.getIsAdmin()) return;
        if (!user.getIsDemo()) return;

        long newTotal = user.getTotalStorageBytes() + fileSize;
        if (newTotal > user.getMaxStorageBytes()) {
            throw new QuotaExceededException("Storage quota exceeded. Maximum " +
                    user.getMaxStorageBytes() / (1024 * 1024) + " MB.");
        }
        if (isNewFile) {
            int newFileCount = user.getFileCount() + 1;
            if (newFileCount > user.getMaxFileCount()) {
                throw new QuotaExceededException("File count quota exceeded. Maximum " +
                        user.getMaxFileCount() + " files.");
            }
        }
    }

    // ========== Exception ==========

    public static class QuotaExceededException extends RuntimeException {
        public QuotaExceededException(String message) { super(message); }
    }
}