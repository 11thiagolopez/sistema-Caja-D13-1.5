package model;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserDAO {
public int login (String user, String password) {
	Connection connection = null;
	PreparedStatement pst = null;
	ResultSet rs = null;
	int state = -1;
	
	try {
		
		String url = "jdbc: sqlite: sistema_d13.db";
		connection = DriverManager.getConnection(url);
		
		String sql = "SELECT id FROM personas WHERE usuario = ? AND password = ?";
				pst = connection.prepareStatement(sql);
		pst.setString(1, user);
		pst.setString(2, password);
		
		rs= pst.executeQuery();
		
		if (rs.next()) {
			state = 1;
		}else {
			state = 0;
		}
			
		}catch (Exception e){
			System.out.println("Error en el login "+ e.getMessage());
			state = -1;
		}finally {
			try {
				if (rs != null) rs.close();
				if (pst != null) pst.close();
				if (connection != null) connection.close();
				
			}catch (Exception e) {
				System.out.println("\"Error al cerrar"+ e.getMessage());
			}
		}
		return state;
	
}
}
