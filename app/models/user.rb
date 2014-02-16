class User < ActiveRecord::Base

  acts_as_authentic do |c|
    c.login_field = 'email'

  end # block optional

  attr_accessible :name, :email, :password, :password_confirmation
end