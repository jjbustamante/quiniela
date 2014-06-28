class User < ActiveRecord::Base
	has_attached_file :photo, :styles => { :small => "150x150>" },
                  :url  => "/assets/users/:id/:style/:basename.:extension",
                  :path => ":rails_root/public/assets/users/:id/:style/:basename.:extension",
                  :default_url => '/assets/missing.jpg'

validates_attachment_size :photo, :less_than => 5.megabytes
validates_attachment_content_type :photo, :content_type => ['image/jpeg', 'image/png']

has_many :quinielas

  acts_as_authentic do |c|
    c.login_field = 'email'

  end # block optional

  attr_accessible :name, :email, :password, :password_confirmation,:photo, :quinielas_count, :quinielas, :is_admin
end