package main.java.com.filesync.client.db;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class LocalMetadataRepository {
    private final Connection connection;

    public LocalMetadataRepository() throws SQLException
    {
        connection = DriverManager.getConnection("jdbc:h2:./client_data/localdb;DB_CLOSE_DELAY=-1");
        createTable();
    }
    private void createTable() throws SQLException
    {
        String sql = "CREATE TABLE IF NOT EXISTS local_file (" +
                "relative_path VARCHAR(255) PRIMARY KEY, " +
                "sha256_hash VARCHAR(64), " +
                "last_sync TIMESTAMP )";
        try
        {
            Statement statement = connection.createStatement();
            statement.execute(sql);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
    public void saveFile(String relativePath, String sha256Hash) throws SQLException
    {
        String sql = "MERGE INTO local_file KEY(relative_path) VALUES (?, ?, CURRENT_TIMESTAMP)";
        try
        {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, relativePath);
            preparedStatement.setString(2, sha256Hash);
            preparedStatement.executeUpdate();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    public Map<String, String> getAllFiles() throws SQLException
    {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT relative_path, sha256_hash FROM local_file";
        try
        {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(sql);

            while (resultSet.next())
            {
                map.put(resultSet.getString("relative_path"), resultSet.getString("sha256_hash"));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return map;
    }

    public void deleteFile(String relativePath) throws SQLException
    {
        String sql = "DELETE FROM local_file WHERE relative_path = ?";
        try
        {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, relativePath);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
